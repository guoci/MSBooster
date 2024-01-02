package Features;

import org.knowm.xchart.BitmapEncoder;
import org.knowm.xchart.CategoryChart;
import org.knowm.xchart.CategoryChartBuilder;
import org.knowm.xchart.Histogram;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;

public class ScoreHistogram {

    Set<String> logScaleFeatures = Set.of("delta_RT_loess", "delta_RT_loess_normalized", "RT_probability_unif_prior",
            "hypergeometric_probability");

    public ScoreHistogram(String pinFile, String feature) throws IOException {
        String xAxisLabel = feature;
        if (logScaleFeatures.contains(feature)) {
            xAxisLabel = "log(" + xAxisLabel + " + 0.01)";
        }
        CategoryChart chart = new CategoryChartBuilder().width(1200).height(800).
                xAxisTitle(xAxisLabel).yAxisTitle("PSMs").build();
        chart.getStyler().setPlotGridLinesVisible(false);
        //chart.getStyler().setXAxisTickMarkSpacingHint(10);
        chart.getStyler().setXAxisMaxLabelCount(10);
        chart.getStyler().setYAxisTickMarkSpacingHint(80);
        chart.getStyler().setXAxisLabelRotation(45);
        chart.getStyler().setXAxisDecimalPattern("#.##");

        PinReader pinReader = new PinReader(pinFile);
        ArrayList<Double> targetScores = new ArrayList<>();
        ArrayList<Double> decoyScores = new ArrayList<>();
        while (pinReader.next()) {
            //collect score values
            Double score = Double.valueOf(pinReader.getColumn(feature));
            if (logScaleFeatures.contains(feature)) {
                score = Math.log10(score + 0.01);
            }
            if (pinReader.getTD() == 1) {
                targetScores.add(score);
            } else {
                decoyScores.add(score);
            }
        }

        //plot histogram
        double scoreMin = Math.min(Collections.min(targetScores), Collections.min(decoyScores));
        double scoreMax = Math.max(Collections.max(targetScores), Collections.max(decoyScores));

        Histogram histT = new Histogram(targetScores, 100, scoreMin, scoreMax);
        Histogram histD = new Histogram(decoyScores, 100, scoreMin, scoreMax);
        chart.addSeries("targets", histT.getxAxisData(), histT.getyAxisData());
        chart.addSeries("decoys", histD.getxAxisData(), histD.getyAxisData());

        String pinPath = new File(pinFile).getParent();
        String name = new File(pinFile).getName();
        if (! new File(pinPath + File.separator + "MSBooster_histograms").exists()) {
            new File(pinPath + File.separator + "MSBooster_histograms").mkdirs();
        }
        BitmapEncoder.saveBitmap(chart, pinPath + File.separator + "MSBooster_histograms" + File.separator +
                        name.substring(0, name.length() - 4) + "_" + feature,
                BitmapEncoder.BitmapFormat.PNG);
    }
}