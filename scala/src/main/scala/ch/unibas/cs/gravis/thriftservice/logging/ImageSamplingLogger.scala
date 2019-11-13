package ch.unibas.cs.gravis.thriftservice.logging

import java.io.{File, FileOutputStream}
import java.text.{DateFormat, SimpleDateFormat}
import java.util.{Calendar, Date}

import javax.swing.JLabel

/**
 * Color fitting accept-reject sampling logger
 *
 * @param label        JPanel which is going to be updated
 * @param logToConsole logging into the console
 * @tparam A RenderParameter
 */
class ImageSamplingLogger[A](label: JLabel, logToConsole: Boolean) extends AcceptRejectLogger[A] {
    private var counter: Int = 0
    private val dateFormat: DateFormat = new SimpleDateFormat("HH-mm-ss, MMM dd")
    private val date: Date = Calendar.getInstance().getTime
    private val saveTime: String = dateFormat.format(date)
    private val fos = new FileOutputStream(new File(s"logging/logs/log_$saveTime.txt"))
    private val verbosePrintLogger = new VerbosePrintLogger[A](Console.out, "")
    private val numAccepted = collection.mutable.Map[String, Int]()
    private val numRejected = collection.mutable.Map[String, Int]()

    override def accept(current: A,
                        sample: A,
                        generator: ProposalGenerator[A],
                        evaluator: DistributionEvaluator[A]): Unit = {
        label.setText(s"Iteration: $counter A logValue: %.2f".format(evaluator.logValue(sample)))
        label.setForeground(RGB(0.0, 0.6, 0.0).toAWTColor)
        val numAcceptedSoFar = numAccepted.getOrElseUpdate(generator.toString, 0)
        numAccepted.update(generator.toString, numAcceptedSoFar + 1)
        if (logToConsole) {
            verbosePrintLogger.accept(current, sample, generator, evaluator)
            Console.withOut(fos) {
                println(s"A; ${generator.toString}; ${evaluator.logValue(sample)}")
            }
        }
        counter += 1
    }

    override def reject(current: A,
                        sample: A,
                        generator: ProposalGenerator[A],
                        evaluator: DistributionEvaluator[A]): Unit = {
        label.setText(s"Iteration: $counter R logValue: %.2f".format(evaluator.logValue(sample)))
        label.setForeground(RGB(0.7, 0.0, 0.0).toAWTColor)
        val numRejectedSoFar = numRejected.getOrElseUpdate(generator.toString, 0)
        numRejected.update(generator.toString, numRejectedSoFar + 1)
        if (logToConsole) {
            Console.withOut(fos) {
                println(s"R; ${generator.toString}; ${evaluator.logValue(sample)}")
            }
            verbosePrintLogger.reject(current, sample, generator, evaluator)
        }
        counter += 1
    }

    def acceptanceRatios(): Map[String, Double] = {
        val generatorNames: Set[String] = numRejected.keys.toSet.union(numAccepted.keys.toSet)
        val acceptanceRatios: Set[(String, Double)] = for (generatorName <- generatorNames) yield {
            val total = (numAccepted.getOrElse(generatorName, 0)
                + numRejected.getOrElse(generatorName, 0)).toDouble
            (generatorName, numAccepted.getOrElse(generatorName, 0) / total)
        }
        acceptanceRatios.toMap
    }
}

object ImageSamplingLogger {
    def apply[A](label: JLabel, logToConsole: Boolean) = new ImageSamplingLogger[A](label, logToConsole)
}
