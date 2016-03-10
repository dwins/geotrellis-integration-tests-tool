package geotrellis.spark.ingest

import geotrellis.raster.resample.{ResampleMethod, NearestNeighbor}
import geotrellis.spark._
import geotrellis.spark.tiling._
import geotrellis.spark.reproject._
import geotrellis.raster._
import geotrellis.proj4._
import org.apache.spark.Partitioner
import org.apache.spark.rdd._
import org.apache.spark.storage.StorageLevel
import scala.reflect.ClassTag

object FIngest {
  /**
   * Represents the ingest process.
   * An ingest process produces a layer from a set of input rasters.
   *
   * The ingest process has the following steps:
   *
   *  - Reproject tiles to the desired CRS:  (CRS, RDD[(Extent, CRS), Tile)]) -> RDD[(Extent, Tile)]
   *  - Determine the appropriate layer meta data for the layer. (CRS, LayoutScheme, RDD[(Extent, Tile)]) -> LayerMetadata)
   *  - Resample the rasters into the desired tile format. RDD[(Extent, Tile)] => RasterRDD[K]
   *  - Optionally pyramid to top zoom level, calling sink at each level
   *
   * Ingesting is abstracted over the following variants:
   *  - The source of the input tiles, which are represented as an RDD of (T, Tile) tuples, where T: ProjectedExtentComponent
   *  - The LayoutScheme which will be used to determine how to retile the input tiles.
   *
   * @param sourceTiles   RDD of tiles that have Extent and CRS
   * @param destCRS       CRS to be used by the output layer
   * @param layoutScheme  LayoutScheme to be used by output layer
   * @param pyramid       Pyramid up to level 1, sink function will be called for each level
   * @param cacheLevel    Storage level to use for RDD caching
   * @param sink          function that utilize the result of the ingest, assumed to force materialization of the RDD
   * @tparam T            type of input tile key
   * @tparam K            type of output tile key, must have SpatialComponent
   * @return
   */
  def apply[T: ProjectedExtentComponent: ClassTag: ? => TilerKeyMethods[T, K], K: SpatialComponent: ClassTag](
      sourceTiles: RDD[(T, Tile)],
      destCRS: CRS,
      layoutScheme: LayoutScheme,
      tileSize: Int = 256,
      pyramid: Boolean = false,
      cacheLevel: StorageLevel = StorageLevel.NONE,
      resampleMethod: ResampleMethod = NearestNeighbor,
      partitioner: Option[Partitioner] = None,
      bufferSize: Option[Int] = None
    )
    (sink: (RasterRDD[K], Int) => Unit): Unit =
  {
    val (_, rasterMetaData) = RasterMetaData.fromRdd(sourceTiles, FloatingLayoutScheme(tileSize))
    val tiledRdd = sourceTiles.tileToLayout(rasterMetaData, resampleMethod).cache()
    val contextRdd = new ContextRDD(tiledRdd, rasterMetaData)
    val (zoom, rasterRdd) = bufferSize.fold(contextRdd.reproject(destCRS, layoutScheme))(contextRdd.reproject(destCRS, layoutScheme, _))
    rasterRdd.persist(cacheLevel)

    def buildPyramid(zoom: Int, rdd: RasterRDD[K]): List[(Int, RasterRDD[K])] = {
      if (zoom >= 1) {
        rdd.persist(cacheLevel)
        sink(rdd, zoom)
        val pyramidLevel @ (nextZoom, nextRdd) = Pyramid.up(rdd, layoutScheme, zoom, partitioner)
        pyramidLevel :: buildPyramid(nextZoom, nextRdd)
      } else {
        sink(rdd, zoom)
        List((zoom, rdd))
      }
    }

    if (pyramid)
      buildPyramid(zoom, rasterRdd)
        .foreach { case (z, rdd) => rdd.unpersist(true) }
    else
      sink(rasterRdd, zoom)
  }
}