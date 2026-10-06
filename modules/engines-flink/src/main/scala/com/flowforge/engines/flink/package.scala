package com.flowforge.engines

import org.apache.flink.streaming.api.datastream.DataStream
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment

package object flink {

  /**
   * How to build a stream of JSON rows, given an environment to build it in.
   *
   * A Flink job graph belongs to one environment and is consumed when that environment executes, so a dataset
   * cannot hold a built `DataStream` and stay usable: the second operation on it would re-run every sink the
   * first one added. A plan holds no environment, so each terminal operation builds the graph fresh in its
   * own and the dataset stays a value that can be used more than once.
   *
   * Building a plan is a calculation. Running one is an action, and every action in this module lives in
   * [[FlinkStreamOps]].
   *
   * The rows are JSON because JSON is the one representation both sides already speak: `DataDecoder` and
   * `DataEncoder` are defined over it, so a FlowForge operation written against `A` can be applied to a row
   * inside a Flink task. This is the same choice the Spark engine makes in `SparkFrameOps`.
   */
  type FlinkPlan = StreamExecutionEnvironment => DataStream[String]
}
