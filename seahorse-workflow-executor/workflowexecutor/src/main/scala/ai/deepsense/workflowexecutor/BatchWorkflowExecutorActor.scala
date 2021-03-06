/**
 * Copyright 2015 deepsense.ai (CodiLime, Inc)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ai.deepsense.workflowexecutor

import akka.actor._

import ai.deepsense.commons.utils.Logging
import ai.deepsense.deeplang.CommonExecutionContext
import ai.deepsense.models.workflows.WorkflowWithResults
import ai.deepsense.workflowexecutor.partialexecution.Execution

class BatchWorkflowExecutorActor(
    executionContext: CommonExecutionContext,
    nodeExecutorFactory: GraphNodeExecutorFactory,
    terminationListener: ActorRef,
    initWorkflow: WorkflowWithResults)
  extends WorkflowExecutorActor(
    executionContext,
    nodeExecutorFactory,
    None,
    None,
    Some(terminationListener),
    Execution.defaultExecutionFactory)
  with Actor
  with Logging {

  initWithWorkflow(initWorkflow)

  override def receive: Actor.Receive = ready()
}

object BatchWorkflowExecutorActor {
  def props(
    ec: CommonExecutionContext,
    statusListener: ActorRef,
    initWorkflow: WorkflowWithResults): Props =
    Props(new BatchWorkflowExecutorActor(
      ec,
      new GraphNodeExecutorFactoryImpl,
      statusListener,
      initWorkflow))
}
