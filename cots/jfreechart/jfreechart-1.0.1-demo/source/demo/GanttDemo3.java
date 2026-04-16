package demo;


import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.Stroke;
import java.awt.geom.Rectangle2D;
import java.util.Calendar;
import java.util.Date;

import javax.swing.JPanel;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.CategoryAxis;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.entity.CategoryItemEntity;
import org.jfree.chart.entity.EntityCollection;
import org.jfree.chart.labels.CategoryItemLabelGenerator;
import org.jfree.chart.labels.CategoryToolTipGenerator;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.DefaultDrawingSupplier;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.renderer.category.CategoryItemRendererState;
import org.jfree.data.category.CategoryDataset;
import org.jfree.data.category.IntervalCategoryDataset;
import org.jfree.data.gantt.GanttCategoryDataset;
import org.jfree.data.gantt.Task;
import org.jfree.data.gantt.TaskSeries;
import org.jfree.data.gantt.TaskSeriesCollection;
import org.jfree.data.time.SimpleTimePeriod;
import org.jfree.ui.ApplicationFrame;
import org.jfree.ui.RectangleEdge;
import org.jfree.ui.RefineryUtilities;

public class GanttDemo3 extends ApplicationFrame {


    public GanttDemo3(String title) {
        super(title);
        JPanel chartPanel = createDemoPanel();
        chartPanel.setPreferredSize(new java.awt.Dimension(500, 270));
        setContentPane(chartPanel);
    }

    /**
     * Creates a sample dataset for a Gantt chart.
     *
     * @return The dataset.
     */
    public static IntervalCategoryDataset createDataset() {

        TaskSeries s1 = new TaskSeries("1");
        s1.add(new Task("Contrakt 1", new SimpleTimePeriod(date(1, Calendar.JANUARY, 2005), date(31, Calendar.JUNE, 2005))));
        TaskSeries s2 = new TaskSeries("2");
        s2.add(new Task("Contrakt 1", new SimpleTimePeriod(date(1, Calendar.JULY, 2005), date(1, Calendar.FEBRUARY, 2006))));
        TaskSeries s3 = new TaskSeries("3");
        s3.add(new Task("Contrakt 2", new SimpleTimePeriod(date(1, Calendar.JANUARY, 2005), date(1, Calendar.FEBRUARY, 2006))));
        TaskSeries s4 = new TaskSeries("4");
        s4.add(new Task("Contrakt 3", new SimpleTimePeriod(date(1, Calendar.JANUARY, 2005), date(1, Calendar.FEBRUARY, 2006))));

        TaskSeriesCollection collection = new TaskSeriesCollection();
        collection.add(s1);
        collection.add(s2);
        collection.add(s3);
        collection.add(s4);

        return collection;
    }

    /**
     * Utility method for creating <code>Date</code> objects.
     *
     * @param day  the date.
     * @param month  the month.
     * @param year  the year.
     *
     * @return a date.
     */
    private static Date date(int day, int month, int year) {

        Calendar calendar = Calendar.getInstance();
        calendar.set(year, month, day);
        Date result = calendar.getTime();
        return result;

    }

    /**
     * Creates a chart.
     *
     * @param dataset  the dataset.
     *
     * @return The chart.
     */
    private static JFreeChart createChart(IntervalCategoryDataset dataset) {
        JFreeChart chart = ChartFactory.createGanttChart("Contract Version Validity Demo", // chart title
                "", // domain axis label
                null, // range axis label
                dataset, // data
                false, // include legend
                true, // tooltips
                false // urls
                );
        CategoryPlot plot = (CategoryPlot) chart.getPlot();
        MyGanttRenderer renderer = new MyGanttRenderer();
        plot.setRenderer(renderer);
        plot.getDomainAxis().setCategoryMargin(0.05);
        plot.getDomainAxis().setMaximumCategoryLabelWidthRatio(10.0f);
        renderer.setDrawBarOutline(false);
        return chart;
    }

    /**
     * Creates a panel for the demo (used by SuperDemo.java).
     *
     * @return A panel.
     */
    public static JPanel createDemoPanel() {
        JFreeChart chart = createChart(createDataset());
        return new ChartPanel(chart);
    }

    /**
     * Starting point for the demonstration application.
     *
     * @param args  ignored.
     */
    public static void main(String[] args) {

        GanttDemo3 demo = new GanttDemo3("Gantt Chart Demo 3");
        demo.pack();
        RefineryUtilities.centerFrameOnScreen(demo);
        demo.setVisible(true);

    }

    static class MyGanttRenderer extends GanttRenderer2 {

        public MyGanttRenderer() {
            super();
        }

        public Paint getItemPaint(int row, int column) {
            return DefaultDrawingSupplier.DEFAULT_PAINT_SEQUENCE[column];
        }
        
        /**
         * Draws a single task.
         *
         * @param g2  the graphics device.
         * @param state  the renderer state.
         * @param dataArea  the data plot area.
         * @param plot  the plot.
         * @param domainAxis  the domain axis.
         * @param rangeAxis  the range axis.
         * @param dataset  the data.
         * @param row  the row index (zero-based).
         * @param column  the column index (zero-based).
         */
        protected void drawTask(Graphics2D g2,
                                CategoryItemRendererState state,
                                Rectangle2D dataArea,
                                CategoryPlot plot,
                                CategoryAxis domainAxis,
                                ValueAxis rangeAxis,
                                GanttCategoryDataset dataset,
                                int row,
                                int column) {

            PlotOrientation orientation = plot.getOrientation();

            RectangleEdge rangeAxisLocation = plot.getRangeAxisEdge();
            
            // Y0
            Number value0 = dataset.getEndValue(row, column);
            if (value0 == null) {
                return;
            }
            double java2dValue0 = rangeAxis.valueToJava2D(value0.doubleValue(), 
                    dataArea, rangeAxisLocation);

            // Y1
            Number value1 = dataset.getStartValue(row, column);
            if (value1 == null) {
                return;
            }
            double java2dValue1 = rangeAxis.valueToJava2D(value1.doubleValue(), 
                    dataArea, rangeAxisLocation);

            if (java2dValue1 < java2dValue0) {
                double temp = java2dValue1;
                java2dValue1 = java2dValue0;
                java2dValue0 = temp;
                Number tempNum = value1;
                value1 = value0;
                value0 = tempNum;
            }

            // count the number of non-null values
            int totalBars = countNonNullValues(dataset, column);  
            if (totalBars == 0) {
                return;
            }
            // count non-null values up to but not including the current value
            int priorBars = countPriorNonNullValues(dataset, column, row); 
            
//            double rectStart = calculateBarW0(plot, orientation, dataArea, 
//                    domainAxis, state, row, column);
//            double rectBreadth = state.getBarWidth();

            double rectBreadth = (domainAxis.getCategoryEnd(column, getColumnCount(), 
                    dataArea, plot.getDomainAxisEdge()) 
                    - domainAxis.getCategoryStart(column, getColumnCount(), 
                    dataArea, plot.getDomainAxisEdge())) / totalBars;
            double rectStart = domainAxis.getCategoryStart(column, getColumnCount(), 
                    dataArea, plot.getDomainAxisEdge()) + rectBreadth * priorBars;
            double rectLength = Math.abs(java2dValue1 - java2dValue0);
            
            Rectangle2D bar = null;
            if (orientation == PlotOrientation.HORIZONTAL) {
                bar = new Rectangle2D.Double(java2dValue0, rectStart, rectLength, 
                        rectBreadth);
            }
            else if (orientation == PlotOrientation.VERTICAL) {
                bar = new Rectangle2D.Double(rectStart, java2dValue1, rectBreadth, 
                        rectLength);
            }

            Rectangle2D completeBar = null;
            Rectangle2D incompleteBar = null;
            Number percent = dataset.getPercentComplete(row, column);
            double start = getStartPercent();
            double end = getEndPercent();
            if (percent != null) {
                double p = percent.doubleValue();
                if (plot.getOrientation() == PlotOrientation.HORIZONTAL) {
                    completeBar = new Rectangle2D.Double(java2dValue0, 
                            rectStart + start * rectBreadth, rectLength * p, 
                            rectBreadth * (end - start));
                    incompleteBar = new Rectangle2D.Double(java2dValue0 
                            + rectLength * p, rectStart + start * rectBreadth, 
                            rectLength * (1 - p), rectBreadth * (end - start));
                }
                else if (plot.getOrientation() == PlotOrientation.VERTICAL) {
                    completeBar = new Rectangle2D.Double(rectStart + start 
                            * rectBreadth, java2dValue1 + rectLength * (1 - p), 
                            rectBreadth * (end - start), rectLength * p);
                    incompleteBar = new Rectangle2D.Double(rectStart + start 
                            * rectBreadth, java2dValue1, rectBreadth 
                            * (end - start), rectLength * (1 - p));
                }
                    
            }

            Paint seriesPaint = getItemPaint(row, column);
            g2.setPaint(seriesPaint);
            g2.fill(bar);

            if (completeBar != null) {
                g2.setPaint(getCompletePaint());
                g2.fill(completeBar);
            }
            if (incompleteBar != null) {
                g2.setPaint(getIncompletePaint());
                g2.fill(incompleteBar);
            }
            
            // draw the outline...
            if (isDrawBarOutline() 
                    && state.getBarWidth() > BAR_OUTLINE_WIDTH_THRESHOLD) {
                Stroke stroke = getItemOutlineStroke(row, column);
                Paint paint = getItemOutlinePaint(row, column);
                if (stroke != null && paint != null) {
                    g2.setStroke(stroke);
                    g2.setPaint(paint);
                    g2.draw(bar);
                }
            }
            
            CategoryItemLabelGenerator generator 
                = getItemLabelGenerator(row, column);
            if (generator != null && isItemLabelVisible(row, column)) {
                drawItemLabel(g2, dataset, row, column, plot, generator, bar, 
                        false);
            }        

            // collect entity and tool tip information...
            if (state.getInfo() != null) {
                EntityCollection entities = state.getEntityCollection();
                if (entities != null) {
                    String tip = null;
                    CategoryToolTipGenerator tipster = getToolTipGenerator(row, 
                            column);
                    if (tipster != null) {
                        tip = tipster.generateToolTip(dataset, row, column);
                    }
                    String url = null;
                    if (getItemURLGenerator(row, column) != null) {
                        url = getItemURLGenerator(row, column).generateURL(dataset, 
                                row, column);
                    }
                    CategoryItemEntity entity = new CategoryItemEntity(bar, tip, 
                            url, dataset, row, dataset.getColumnKey(column), 
                            column);
                    entities.add(entity);
                }
            }

        }
        
        private int countNonNullValues(CategoryDataset dataset, int column) {
            return countPriorNonNullValues(dataset, column, dataset.getRowCount());
        }
        
        private int countPriorNonNullValues(CategoryDataset dataset, int column, 
                int row) {
            if (row == 0) {
                return 0;
            }
            int count = 0;
            for (int r = 0; r < row; r++) {
                if (dataset.getValue(r, column) != null) {
                    count++;
                }
            }
            return count;
        }

    }

}
