package geotrellis.test.multiband.file

import geotrellis.config.json.backend.JCredentials
import geotrellis.raster.MultibandTile
import geotrellis.spark._
import geotrellis.spark.io._
import geotrellis.test.FileTest
import geotrellis.test.multiband.load.HadoopLoad
import geotrellis.vector.ProjectedExtent
import geotrellis.config.json.dataset.JConfig

import org.apache.spark.SparkContext

abstract class HadoopIngestTest(jConfig: JConfig, jCredentials: JCredentials) extends FileTest[ProjectedExtent, SpatialKey, MultibandTile](jConfig, jCredentials) with HadoopLoad

object HadoopIngestTest {
  def apply(implicit jConfig: JConfig, jCredentials: JCredentials, _sc: SparkContext) = new HadoopIngestTest(jConfig, jCredentials) {
    @transient implicit val sc = _sc
  }
}
