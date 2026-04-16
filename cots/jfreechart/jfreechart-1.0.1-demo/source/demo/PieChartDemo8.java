/* ------------------
 * PieChartDemo8.java
 * ------------------
 * (C) Copyright 2006, by Object Refinery Limited.
 *
 */

package demo;

import java.awt.Dimension;
import java.awt.GridLayout;

import javax.swing.JPanel;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PiePlot;
import org.jfree.chart.title.TextTitle;
import org.jfree.data.general.DefaultPieDataset;
import org.jfree.data.general.PieDataset;
import org.jfree.ui.ApplicationFrame;
import org.jfree.ui.RefineryUtilities;

/**
 * A demo showing four pie charts of the same dataset which contains a null
 * value, a zero value and a negative value.  Each chart has a different 
 * setting for the ignoreNullValues and ignoreZeroValues flags.
 */
public class PieChartDemo8 extends ApplicationFrame {

    /**
     * Creates a new demo instance.
     * 
     * @param title  the frame title.
     */
    public PieChartDemo8(String title) {

        super(title);
        JPanel panel = createDemoPanel();
        panel.setPreferredSize(new Dimension(800, 600));
        setContentPane(panel);

    }

    /**
     * Creates a sample dataset containing a null value, a zero value and a 
     * negative value.
     * 
     * @return A sample dataset.
     */
    private static PieDataset createDataset() {
        DefaultPieDataset dataset = new DefaultPieDataset();
        dataset.setValue("S1", 7.0);
        dataset.setValue("S2", null);
        dataset.setValue("S3", 0.0);
        dataset.setValue("S4", -1.0);
        dataset.setValue("S5", 3.0);
        return dataset;        
    }

    /**
     * Creates a chart with the specified title and dataset.
     * 
     * @param title  the chart title.
     * @param dataset  the dataset.
     * 
     * @return A chart.
     */
    private static JFreeChart createChart(String title, PieDataset dataset) {
        JFreeChart chart = ChartFactory.createPieChart(title, dataset, true, 
                true, false);
        return chart;
    }
    
    /**
     * Creates a panel for the demo (used by SuperDemo.java).
     * 
     * @return A panel.
     */
    public static JPanel createDemoPanel() {
        JPanel panel = new JPanel(new GridLayout(2, 2));
        JFreeChart chart1 = createChart("Chart 1", createDataset());
        chart1.addSubtitle(new TextTitle("Ignore nulls: false, zeros: false"));

        JFreeChart chart2 = createChart("Chart 2", createDataset());
        chart2.addSubtitle(new TextTitle("Ignore nulls: true, zeros: false"));
        PiePlot plot2 = (PiePlot) chart2.getPlot();
        plot2.setIgnoreNullValues(true);
        plot2.setIgnoreZeroValues(false);

        JFreeChart chart3 = createChart("Chart 3", createDataset());
        chart3.addSubtitle(new TextTitle("Ignore nulls: false, zeros: true"));
        PiePlot plot3 = (PiePlot) chart3.getPlot();
        plot3.setIgnoreNullValues(false);
        plot3.setIgnoreZeroValues(true);

        JFreeChart chart4 = createChart("Chart 4", createDataset());
        chart4.addSubtitle(new TextTitle("Ignore nulls: true, zeros: true"));
        PiePlot plot4 = (PiePlot) chart4.getPlot();
        plot4.setIgnoreNullValues(true);
        plot4.setIgnoreZeroValues(true);

        panel.add(new ChartPanel(chart1));
        panel.add(new ChartPanel(chart2));
        panel.add(new ChartPanel(chart3));
        panel.add(new ChartPanel(chart4));
        return panel;
    }
    
    /**
     * The starting point for the demo.
     * 
     * @param args  ignored.
     */
    public static void main(String[] args) {
        PieChartDemo8 demo = new PieChartDemo8("Pie Chart Demo 8");
        demo.pack();
        RefineryUtilities.centerFrameOnScreen(demo);
        demo.setVisible(true);
    }

}
