/**
 * Copyright 2015, deepsense.io
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

package io.deepsense.deeplang.params.choice

import spray.json.{JsArray, JsObject, JsString}

import io.deepsense.deeplang.params.BooleanParam

sealed trait ChoiceABC extends Choice

case class OptionA() extends ChoiceABC {
  override val name = "A"
  override val index = 2

  val bool = BooleanParam(
    name = "bool",
    description = "description")

  def setBool(b: Boolean): this.type = set(bool, b)
}

case class OptionB() extends ChoiceABC {
  override val name = "B"
  override val index = 0
}

case class OptionC() extends ChoiceABC {
  override val name = "C"
  override val index = 1
}

sealed trait BaseChoice extends Choice

case class ChoiceWithoutNoArgConstructor(x: String) extends BaseChoice {
  override val name: String = "choiceWithoutNoArgConstructor"
}

object ChoiceFixtures {

  val values = "values" -> JsArray(
    JsObject(
      "name" -> JsString("B"),
      "schema" -> JsArray()
    ),
    JsObject(
      "name" -> JsString("C"),
      "schema" -> JsArray()
    ),
    JsObject(
      "name" -> JsString("A"),
      "schema" -> JsArray(
        JsObject(
          "type" -> JsString("boolean"),
          "name" -> JsString("bool"),
          "description" -> JsString("description")
        )
      )
    )
  )
}
