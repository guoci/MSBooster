/*
 * This file is part of MSBooster.
 *
 * MSBooster is free software: you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 *
 * MSBooster is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with MSBooster. If not, see <https://www.gnu.org/licenses/>.
 */

package Features;

import org.knowm.xchart.BitmapEncoder;
import org.knowm.xchart.XYChart;
import org.knowm.xchart.XYChartBuilder;
import org.knowm.xchart.XYSeries;
import org.knowm.xchart.style.Styler;

import java.awt.*;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

public class RTCalibrationFigure {
    public RTCalibrationFigure(MzmlReader mzml, String outFile, float opacity) throws IOException {
        String pinPath = new File(outFile).getParent();
        String pinName = new File(outFile).getName();

        XYChart chart = new XYChartBuilder().width(1000).height(1000).build();
        chart.getStyler().setDefaultSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Scatter);
        chart.getStyler().setChartTitleVisible(true);
        chart.setTitle(mzml.pathStr);
        chart.setXAxisTitle("experimental RT");
        chart.setYAxisTitle("predicted RT");
        chart.getStyler().setLegendVisible(false);
        chart.getStyler().setMarkerSize(8);
        chart.getStyler().setYAxisGroupPosition(0, Styler.YAxisPosition.Right);
        chart.getStyler().setLegendVisible(true);
        chart.getStyler().setLegendPosition(Styler.LegendPosition.InsideNW);

        // Series
        List<Float> xData = new ArrayList<Float>();
        List<Float> yData = new ArrayList<Float>();
        List<Float> eScores = new ArrayList<Float>();

        //for PTMs besides oxM and C57
        List<List<Float>> xDataMod = new ArrayList<>();
        List<List<Float>> yDataMod = new ArrayList<>();
        float minRT = Float.MAX_VALUE;
        float maxRT = Float.MIN_VALUE;

        String[] masses;
        if (Constants.RTfigure_masses.equals("")) {
            masses = new String[0];
        } else {
            masses = Constants.RTfigure_masses.split(",");
            for (int i = 0; i < masses.length; i++) {
                masses[i] = masses[i].trim();
                xDataMod.add(new ArrayList<>());
                yDataMod.add(new ArrayList<>());
            }
        }
        for (int scanNum : new TreeSet<Integer>(mzml.scanNumberObjects.keySet())) {
            MzmlScanNumber scanNumObj = mzml.getScanNumObject(scanNum);
            float rt = scanNumObj.RT; //experimental RT for this scan
            if (rt < minRT) {
                minRT = rt;
            }
            if (rt > maxRT) {
                maxRT = rt;
            }

            for (int i = 1; i < scanNumObj.peptideObjects.size() + 1; i++) {
                PeptideObj pep = scanNumObj.getPeptideObject(i);
                //only get best ones
                if (Float.parseFloat(pep.escore) < Constants.RTescoreCutoff && pep.spectralSimObj.predIntensities[0] != 0f) {
                    eScores.add(Float.parseFloat(pep.escore));
                } else {
                    break;
                }
            }
        }

        eScores.sort(Comparator.naturalOrder());
        float maxEScore = eScores.get(Math.min(Constants.numRTplot, eScores.size()) - 1);
        for (int scanNum : new TreeSet<Integer>(mzml.scanNumberObjects.keySet())) {
            MzmlScanNumber scanNumObj = mzml.getScanNumObject(scanNum);
            float rt = scanNumObj.RT; //experimental RT for this scan
            for (int i = 1; i < scanNumObj.peptideObjects.size() + 1; i++) {
                PeptideObj pep = scanNumObj.getPeptideObject(i);
                //only get best ones
                if (Float.parseFloat(pep.escore) <= maxEScore &&
                        pep.spectralSimObj.predIntensities[0] != 0f) {
                    boolean containsMod = false;
                    int modIndex = 0;
                    for (int j = 0; j < masses.length; j++) {
                        String mass = masses[j];
                        if (pep.name.contains(mass)) {
                            containsMod = true;
                            modIndex = j;
                            break;
                        }
                    }
                    if (! containsMod) {
                        xData.add(rt);
                        yData.add(pep.RT);
                    } else {
                        xDataMod.get(modIndex).add(rt);
                        yDataMod.get(modIndex).add(pep.RT);
                    }
                } else {
                    break;
                }
            }
        }

        if (xData.size() > 0) {
            XYSeries series = chart.addSeries("scatter", xData, yData);
            series.setMarkerColor(new Color(0, 0, 0, opacity));
        }

        for (int i = 0; i < xDataMod.size(); i++) {
            List<Float> ix = xDataMod.get(i);
            List<Float> iy = yDataMod.get(i);
            if (ix.size() > 0) {
                XYSeries seriesMod = chart.addSeries("scatterMods - " + masses[i], ix, iy);
                seriesMod.setMarkerColor(new Color(65 * i % 255, 105 * i % 255, 225 * i % 255));
            }
        }

        //loess regression
        // generates Log data
        List<Float> x1Data = new ArrayList<Float>();
        List<Double> y1Data = new ArrayList<Double>();
//        BufferedWriter calibrationPoints = new BufferedWriter(new FileWriter(
//                pinPath + File.separator + "MSBooster_RTplots" + File.separator +
//                        pinName.substring(0, pinName.length() - 4) + "_calibration.csv"));
//        calibrationPoints.write("experimental RT,predicted RT");
        for (float i = minRT; i < maxRT; i = i + (maxRT - minRT) / 1000f) {
            x1Data.add(i);
            double y = mzml.RTLOESS.invoke((double) i);
            y1Data.add(y);
//            calibrationPoints.write(i + "," + y);
        }
//        calibrationPoints.close();
        XYSeries series1 = chart.addSeries("regression", x1Data, y1Data);
        series1.setMarkerColor(new Color(243, 9, 9));
        //series.setLineWidth(1);
        //series.setLineColor(XChartSeriesColors.RED);

//        BitmapEncoder.saveBitmap(chart, "C:/Users/yangkl/Downloads/proteomics/hla/Sample_Chart",
//                BitmapEncoder.BitmapFormat.PNG);
        if (! new File(pinPath + File.separator + "MSBooster_RTplots").exists()) {
            new File(pinPath + File.separator + "MSBooster_RTplots").mkdirs();
        }
        BitmapEncoder.saveBitmap(chart, pinPath + File.separator + "MSBooster_RTplots" + File.separator +
                        pinName.substring(0, pinName.length() - 4),
                BitmapEncoder.BitmapFormat.PNG);
    }
}
