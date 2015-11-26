package au.csiro.obr17q.variantspark.algo

import org.apache.spark.rdd.RDD
import org.apache.spark.mllib.linalg.Vector
import org.apache.spark.mllib.linalg.Vectors
import java.util.Arrays
import org.apache.spark.SparkContext
import org.apache.spark.mllib.linalg.SparseVector

object WideKMeans {
  def sqr(d:Double) = d*d
}

class WideKMeans(kk:Int, kiter:Int) {
  
  // the general ideas it to use kmeans on wide representation
  
  // return cluster centers as RDD[Vector]
  
  def run(data: RDD[Vector]):RDD[Vector] = {
    
    val k = kk
    val iter = kiter
    /// initial centre selection is just a projection of the (can be random)
    
    val dims = data.first().size
    val initialCenters = data.map { v =>
        val d = v.toArray
        // this should be taking random k indexes out of dims
        Vectors.dense( Range(0,k).map(i => d(i)).toArray)
    }
   
    // now we need to run iteratios
    // so first cluster assignmet
    // and for this we need to find distance of each point to it's centre
    var clusterCentres = initialCenters
    
    for(i <- Range(0,iter)) {
    
  //    clusterCentres.cache()
      val clusterAssignment = data.zip(clusterCentres)
        .aggregate(Array.fill(dims)(Array.fill(k)(0.0)))(
            (dists, vac) => {
              val dv = vac._1.toArray
              for(i <- Range(0,dims); j<- Range(0,k)) {dists(i)(j) += WideKMeans.sqr(dv(i) - vac._2(j)) }
              dists
            } ,  // add sqr(dists - vectorsAndCenters) for all (i, j)
            (dist1, dist2) => { 
              for(i <- Range(0,dims); j<- Range(0,k)) {dist1(i)(j) += dist2(i)(j) }
              dist1 
            } // just sum them together
        )
      .map(v => v.zipWithIndex.min._2) // should be find the index of min distance
      // 
      
      val clusterSizes = Array.fill(k)(0)
      clusterAssignment.foreach(c=> clusterSizes(c) += 1)
      
      val br_clusterAssignment = data.context.broadcast(clusterAssignment)
      val br_clusterSizes = data.context.broadcast(clusterSizes)
      // also count and broadcast cluster membership arity
      
      // now calculate new cluster centers
      val newClusterCentres = data.map( v => {
        val contributions:Array[Double] = Array.fill(k)(0.0)
        val clusterAssignment =  br_clusterAssignment.value
        val clusterSizes = br_clusterSizes.value
        Range(0,dims).foreach(i => contributions(clusterAssignment(i)) += v(i)/clusterSizes(clusterAssignment(i)))
        // for each in cluster assignment add a contribution to a corresponging cluster
        Vectors.dense(contributions)
      })
//      clusterCentres.unpersist()
      clusterCentres = newClusterCentres
    }
    clusterCentres
  }
  
  def assignClusters(data:RDD[Vector], clusterCentres:RDD[Vector]):Array[Int] = {
     val dims = data.first().size
     val k = clusterCentres.first().size
     val clusterAssignment = data.zip(clusterCentres)
        .aggregate(Array.fill(dims)(Array.fill(k)(0.0)))(
            (dists, vac) => {
              val dv = vac._1.toArray
              for(i <- Range(0,dims); j<- Range(0,k)) {dists(i)(j) += WideKMeans.sqr(dv(i) - vac._2(j)) }                
              dists
            } ,  // add sqr(dists - vectorsAndCenters) for all (i, j)
            (dist1, dist2) => { 
              for(i <- Range(0,dims); j<- Range(0,k)) {dist1(i)(j) += dist2(i)(j) }
              dist1 
            } // just sum them together
        )
      .map(v => v.zipWithIndex.min._2) // should be find the index of min distance 
      clusterAssignment
  }
  
}