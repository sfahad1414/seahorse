/**
 * Copyright 2016 deepsense.ai (CodiLime, Inc)
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

package ai.deepsense.deeplang.doperations.examples

import ai.deepsense.deeplang.doperables.Projector.ColumnProjection
import ai.deepsense.deeplang.doperables.Projector.RenameColumnChoice.Yes
import ai.deepsense.deeplang.doperations.Projection
import ai.deepsense.deeplang.params.selections.NameSingleColumnSelection

class ProjectionExample extends AbstractOperationExample[Projection] {
  override def dOperation: Projection = {
    val op = new Projection()
    op.transformer.setProjectionColumns(Seq(
      ColumnProjection().setOriginalColumn(NameSingleColumnSelection("price")),
      ColumnProjection().setOriginalColumn(NameSingleColumnSelection("city")),
      ColumnProjection().setOriginalColumn(NameSingleColumnSelection("city"))
        .setRenameColumn(new Yes().setColumnName("location"))
    ))
    op.set(op.transformer.extractParamMap())
  }

  override def fileNames: Seq[String] = Seq("example_city_beds_price")
}
