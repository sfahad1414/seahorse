/**
 * Copyright (c) 2015, CodiLime Inc.
 */

package io.deepsense.workflowmanager

import scala.concurrent.{ExecutionContext, Future}

import com.google.inject.Inject
import com.google.inject.assistedinject.Assisted
import com.google.inject.name.Named
import org.joda.time.DateTime
import spray.json._

import io.deepsense.commons.auth.usercontext.UserContext
import io.deepsense.commons.auth.{Authorizator, AuthorizatorProvider}
import io.deepsense.commons.models.Id
import io.deepsense.commons.utils.Logging
import io.deepsense.deeplang.inference.InferContext
import io.deepsense.graph.CyclicGraphException
import io.deepsense.models.workflows._
import io.deepsense.workflowmanager.exceptions.WorkflowNotFoundException
import io.deepsense.workflowmanager.storage.{NotebookStorage, WorkflowResultsStorage, WorkflowStorage}

/**
 * Implementation of Workflow Manager.
 */
class WorkflowManagerImpl @Inject()(
    authorizatorProvider: AuthorizatorProvider,
    workflowStorage: WorkflowStorage,
    workflowResultsStorage: WorkflowResultsStorage,
    notebookStorage: NotebookStorage,
    inferContext: InferContext,
    @Assisted userContextFuture: Future[UserContext],
    @Named("roles.workflows.get") roleGet: String,
    @Named("roles.workflows.update") roleUpdate: String,
    @Named("roles.workflows.create") roleCreate: String,
    @Named("roles.workflows.delete") roleDelete: String)
    (implicit ec: ExecutionContext)
  extends WorkflowManager with Logging {

  private def authorizator: Authorizator = authorizatorProvider.forContext(userContextFuture)

  def get(id: Id): Future[Option[Either[String, WorkflowWithKnowledge]]] = {
    logger.debug("Get workflow id: {}", id)
    authorizator.withRole(roleGet) { userContext =>
      workflowStorage.get(id).map {
        _.map { _.right.map(withKnowledge(id, _)) }
      }
    }
  }

  def download(id: Id): Future[Option[Either[String, WorkflowWithVariables]]] = {
    logger.debug("Download workflow id: {}", id)
    authorizator.withRole(roleGet) { userContext =>
      getWorkflowWithNotebook(id).map {
          _.map { _.right.map(withVariables(id, _))
        }
      }
    }
  }

  def update(workflowId: Id, workflow: Workflow): Future[WorkflowWithKnowledge] = {
    logger.debug(s"Update workflow id: $workflowId, workflow: $workflow")
    whenGraphAcyclic(workflow) {
      authorizator.withRole(roleUpdate) { userContext =>
        workflowStorage.get(workflowId).flatMap {
          case Some(_) => {
            val notebook = extractNotebookJson(workflow)
            val workflowWithoutNotebook = workflowWithRemovedNotebook(workflow)
            workflowStorage.save(workflowId, workflowWithoutNotebook).map(_ => {
              notebook.map(notebookJson => notebookStorage.save(workflowId, notebookJson.toString))
              withKnowledge(workflowId, workflow)
            })
          }
          case None => throw new WorkflowNotFoundException(workflowId)
        }
      }
    }
  }

  def create(workflow: Workflow): Future[WorkflowWithKnowledge] = {
    logger.debug("Create workflow: {}", workflow)
    whenGraphAcyclic(workflow) {
      authorizator.withRole(roleCreate) {
        userContext => {
          val workflowId = Workflow.Id.randomId
          workflowStorage.save(workflowId, workflow).map(_ => withKnowledge(workflowId, workflow))
        }
      }
    }
  }

  def delete(id: Id): Future[Boolean] = {
    logger.debug("Delete workflow id: {}", id)
    authorizator.withRole(roleDelete) { userContext =>
      workflowStorage.get(id).flatMap {
        case Some(workflow) =>
          workflowStorage.delete(id).map(_ => true)
        case None => Future.successful(false)
      }
    }
  }

  def saveWorkflowResults(
    workflowWithResults: WorkflowWithResults): Future[WorkflowWithSavedResults] = {
    authorizator.withRole(roleCreate) { userContext =>
      val resultsId = ExecutionReportWithId.Id.randomId
      val workflowWithSavedResults =
        WorkflowWithSavedResults(resultsId, workflowWithResults)

      workflowStorage.saveExecutionResults(workflowWithSavedResults)
        .map(_ => workflowResultsStorage.save(workflowWithSavedResults))
        .map(_ => workflowWithSavedResults)
    }
  }

  override def getLatestExecutionReport(
      workflowId: Workflow.Id): Future[Option[Either[String, WorkflowWithSavedResults]]] = {
    authorizator.withRole(roleGet) { userContext =>
      workflowStorage.getLatestExecutionResults(workflowId)
    }
  }


  override def getResultsUploadTime(workflowId: Workflow.Id): Future[Option[DateTime]] = {
    authorizator.withRole(roleGet) { _ =>
      workflowStorage.getResultsUploadTime(workflowId)
    }
  }

  override def getExecutionReport(
      id: ExecutionReportWithId.Id): Future[Option[Either[String, WorkflowWithSavedResults]]] = {
    authorizator.withRole(roleGet) { userContext =>
      workflowResultsStorage.get(id)
    }
  }

  override def getNotebook(workflowId: Workflow.Id): Future[Option[String]] = {
    authorizator.withRole(roleGet) { _ =>
      notebookStorage.get(workflowId)
    }
  }

  override def saveNotebook(workflowId: Workflow.Id, notebook: String): Future[Unit] = {
    authorizator.withRole(roleUpdate) { _ =>
      notebookStorage.save(workflowId, notebook)
    }
  }

  private def withKnowledge(id: Workflow.Id, workflow: Workflow): WorkflowWithKnowledge = {
    val knowledge = workflow.graph.inferKnowledge(inferContext)
    WorkflowWithKnowledge(id, workflow.metadata, workflow.graph, workflow.additionalData, knowledge)
  }

  private def withVariables(id: Workflow.Id, workflow: Workflow): WorkflowWithVariables = {
    WorkflowWithVariables(
      id, workflow.metadata, workflow.graph, workflow.additionalData, Variables())
  }

  private def getWorkflowWithNotebook(id: Workflow.Id): Future[Option[Either[String, Workflow]]] = {
    workflowStorage.get(id).flatMap { maybeResult =>
      maybeResult match {
        case Some(workflowStorageResult) => workflowStorageResult.fold(
          stringWorkflow => stringWorkflowWithNotebook(id, stringWorkflow),
          objectWorkflow => objectWorkflowWithNotebook(id, objectWorkflow))
        case None => Future.successful(None)
      }
    }
  }

  private def stringWorkflowWithNotebook(
      id: Workflow.Id, stringWorkflow: String): Future[Option[Either[String, Workflow]]] = {
    notebookStorage.get(id).map {
      _ match {
        case Some(notebook) => {
          val notebookJson = notebook.parseJson
          val workflowJson = stringWorkflow.parseJson.asJsObject
          val thirdPartyData = workflowJson.fields("thirdPartyData").asJsObject
          val thirdPartyDataWithNotebook = JsObject(
            thirdPartyData.fields.updated("notebook", notebookJson))
          val workflowWithNotebook = JsObject(
            workflowJson.fields.updated("thirdPartyData", thirdPartyDataWithNotebook))
          Some(Left(workflowWithNotebook.toString))
        }
        case None => Some(Left(stringWorkflow))
      }
    }
  }

  private def objectWorkflowWithNotebook(id: Workflow.Id, workflow: Workflow)
      : Future[Option[Either[String, Workflow]]] = {
    notebookStorage.get(id).map {
      _ match {
        case Some(notebook) => {
          val notebookJson = notebook.parseJson
          val additionalDataJson = workflow.additionalData.data.parseJson.asJsObject
          val enrichedAdditionalDataJson = JsObject(
            additionalDataJson.fields.updated("notebook", notebookJson))
          Some(Right(Workflow(
            workflow.metadata,
            workflow.graph,
            ThirdPartyData(enrichedAdditionalDataJson.toString))))
        }
        case None => Some(Right(workflow))
      }
    }
  }

  private def extractNotebookJson(workflow: Workflow): Option[JsValue] =
    workflow.additionalData.data.parseJson.asJsObject.fields.get("notebook")

  private def workflowWithRemovedNotebook(workflow: Workflow): Workflow = {
    val thirdPartyDataJson = workflow.additionalData.data.parseJson.asJsObject
    val prunedThirdPartyData = ThirdPartyData(
      JsObject((thirdPartyDataJson.fields - "notebook")).toString)
    Workflow(workflow.metadata, workflow.graph, prunedThirdPartyData)
  }

  private def whenGraphAcyclic[T](workflow: Workflow)(f: => Future[T]): Future[T] = {
    workflow.graph.topologicallySorted match {
      case Some(_) => f
      case None => Future.failed(new CyclicGraphException())
    }
  }
}
