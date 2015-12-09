package au.csiro.obr17q.variantspark.algo

import scala.Range
import scala.collection.JavaConversions.mapAsScalaMap

import org.apache.spark.mllib.linalg.Vector
import org.apache.spark.rdd.RDD

import au.csiro.obr17q.variantspark.metrics.Metrics
import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap

case class WideRandomForestModel(trees: List[WideDecisionTreeModel], val labelCount:Int) {
  def printout() {
    trees.zipWithIndex.foreach {
      case (tree, index) =>
        println(s"Tree: ${index}")
        tree.printout()
    }
  }

  def variableImportance: Map[Long, Double] = {
    // average by all trees
    val accumulations = new Long2DoubleOpenHashMap();
    val counts = new Long2IntOpenHashMap();
    trees.foreach { t =>
      val treeImportnace = t.variableImportanceAsFastMap
      //println(treeImportnace.toMap)
      treeImportnace.foreach {
        case (index, imp) =>
          accumulations.addTo(index, imp)
          counts.addTo(index, 1)
      }
    }
    accumulations.map { case (index, value) => (index.toLong, value.toDouble/trees.size) }.toMap
  }
  
  def predict(data: RDD[Vector]): Array[Int] = {
    val sampleCount = data.first.size
    // for classification we just do majority vote
    val votes = Array.fill(sampleCount)(Array.fill(labelCount)(0))
    trees.map(_.predict(data)).foreach { x => x.zipWithIndex.foreach{ case (v, i) => votes(i)(v)+=1}} // this is each tree vote for eeach sample
    // now for each sample find the label with the highest count
    votes.map(_.zipWithIndex.max._2)
  }

  def predictIndexed(data: RDD[(Vector,Long)]): Array[Int] = {
    val sampleCount = data.first._1.size
    // for classification we just do majority vote
    val votes = Array.fill(sampleCount)(Array.fill(labelCount)(0))
    trees.map(_.predictIndexed(data)).foreach { x => x.zipWithIndex.foreach{ case (v, i) => votes(i)(v)+=1}} // this is each tree vote for eeach sample
    // now for each sample find the label with the highest count
    votes.map(_.zipWithIndex.max._2)
  }
  
}

case class RandomForestParams(
    val oob:Boolean = true,
    val nTryFraction:Double =  Double.NaN
)

class WideRandomForest {
  def run(data: RDD[(Vector, Long)], labels: Array[Int], ntrees: Int, params:RandomForestParams = RandomForestParams()): WideRandomForestModel = {
    // subsample 
    val dims = labels.length
    val labelCount = labels.max + 1
    val oobVotes = Array.fill(dims)(Array.fill(labelCount)(0))
    
    val ntryFraction = if (params.nTryFraction.isNaN() ) Math.sqrt(dims.toDouble)/dims.toDouble else params.nTryFraction
    println(s"RF: Using ntryfraction: ${ntryFraction}")
    
    val trees = Range(0, ntrees).map { i =>
      println(s"Building tree: ${i}")
      val currentSample = for (i <- 0 until dims) yield (Math.random() * dims).toInt // sample with replacemnt
      val tree = new WideDecisionTree().run(data, labels, currentSample.toArray, ntryFraction)
      val error = if (params.oob) {
        // check which indexes are out of bag
        val inBag = currentSample.distinct.toSet // 
        println(s"oob size: ${dims-inBag.size}")
        // predict on projected data
        // tree.predict() on projected data
        // looks like I actually need to get the reversed set anyway
        val oobIndexes = Range(0,dims).toSet.diff(inBag)
        val predictions = tree.predictIndexed(data.map( t => (WideDecisionTree.projectVector(oobIndexes,false)(t._1), t._2)))
        val indexs = oobIndexes.toSeq.sorted
        predictions.zip(indexs).foreach{ case (v, i) => oobVotes(i)(v)+=1}
        Metrics.classificatoinError(labels, oobVotes.map(_.zipWithIndex.max._2))
      } else {
        Double.NaN
      }
      println(s"Tree error: ${error}")
      (tree, error)
    }
    val error = trees.map(_._2).sum.toDouble / ntrees
    println(s"Error: ${error}")
    WideRandomForestModel(trees.map(_._1).toList, labelCount)
  }
}




