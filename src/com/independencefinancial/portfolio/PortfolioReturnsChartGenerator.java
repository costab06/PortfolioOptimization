package com.bcfinancial.portfolio;


import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtilities;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.general.DefaultPieDataset;
import org.jfree.data.time.Day;
import org.jfree.data.xy.XYDataset;
import org.jfree.date.MonthConstants;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.jfree.ui.RectangleInsets;

import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import java.io.*;
import java.util.*;


public class PortfolioReturnsChartGenerator {
    
    public static JPanel getChartPanel(String title, double[] returns) {
        JFreeChart chart = createChart(title,createDataset(returns));
	ChartPanel chartPanel = new ChartPanel(chart);
	//chartPanel.setTitle(title);
        chartPanel.setPreferredSize(new java.awt.Dimension(500, 270));
	return chartPanel;
    }
    
    public static byte[] getChartPNG(String title, double[] returns) {
        JFreeChart chart = createChart(title,createDataset(returns));
	ByteArrayOutputStream baos = new ByteArrayOutputStream();
	try {
	    ChartUtilities.writeChartAsPNG(baos, chart, 500, 270);
	} catch (Exception e) {
	    System.out.println("Exception creating PNG: "+e);
	}
	return baos.toByteArray();
    }
    
    
    private static JFreeChart createChart(String title, XYDataset dataset) {
	
        JFreeChart chart = 
	    ChartFactory.createXYLineChart(
					   title,// chart title
					   "Sorted Returns",// x axis label
					   "Return", // y axis label
					   dataset,                  // data
					   PlotOrientation.VERTICAL, 
					   true,                     // include legend
					   true,                     // tooltips
					   false                     // urls
					   );
	
	
	chart.setBackgroundPaint(Color.white);
	
        XYPlot plot = (XYPlot) chart.getPlot();
	
        plot.setDomainGridlinePaint(Color.lightGray);
        plot.setRangeGridlinePaint(Color.lightGray);
	
        XYLineAndShapeRenderer renderer 
            = (XYLineAndShapeRenderer) plot.getRenderer();
	
        renderer.setSeriesLinesVisible(0, true);
        renderer.setSeriesShapesVisible(0, false);
        plot.setRenderer(renderer);
	
	
        // change the auto tick unit selection to integer units only...
	//        NumberAxis rangeAxis = (NumberAxis) plot.getRangeAxis();
        //rangeAxis.setStandardTickUnits(NumberAxis.createIntegerTickUnits());
	
        return chart;
    }
    
    private static synchronized XYDataset createDataset(double[] returns) {
	XYSeriesCollection dataset = new XYSeriesCollection();
	
	XYSeries series = new XYSeries("Sorted Returns");
	
	for (int i=0;i<returns.length;i++) {
	    System.out.println("Adding "+returns[i]+" to chart");
	    series.add((double)i, returns[i]);
	}
	dataset.addSeries(series);
	return dataset;
    }
    
}
