package geotrellis.test.multiband.s3

import geotrellis.test.multiband.load.S3Load
import geotrellis.util.S3Support
import org.apache.spark.SparkContext

class S3IngestTests(@transient implicit val sc: SparkContext) extends Tests with S3Support with S3Load

object S3IngestTests {
  def apply(implicit sc: SparkContext) = new S3IngestTests()
}