package com.bcfinancial.portfolio;

import java.util.Date;
import com.bcfinancial.data.converter.*;
import com.bcfinancial.persistenceManager.Persistable;
import jakarta.persistence.*;

@Entity
@Table(name = "portfolio_optimizations")
@Access(AccessType.FIELD)
public class PortfolioOptimization extends Persistable {

    @Id
    @Column(name = "portfolioOptimizationName", length = 255, nullable = false)
    private String portfolioOptimizationName;

    @Column(name = "state", length = 255, nullable = false)
    private String state;

    @Convert(converter = PortfolioCalculatedDataConverter.class)
    @Column(name = "sp500", columnDefinition = "BLOB")
    private PortfolioCalculatedData sp500;

    @Convert(converter = PortfolioCalculatedDataConverter.class)
    @Column(name = "original_data", columnDefinition = "BLOB")
    private PortfolioCalculatedData original;

    @Convert(converter = PortfolioCalculatedDataArrayConverter.class)
    @Column(name = "optimized", columnDefinition = "LONGBLOB")
    private PortfolioCalculatedData[] optimized;

    @Convert(converter = PortfolioEntityArrayConverter.class)
    @Column(name = "portfolioArray", columnDefinition = "LONGBLOB")
    private Portfolio[] portfolioArray;

    @Convert(converter = DoubleMat2DConverter.class)
    @Column(name = "returnsArray", columnDefinition = "LONGBLOB")
    private double[][] returnsArray;

    public PortfolioOptimization() {} // required by JPA

    public PortfolioOptimization(String portfolioOptimizationName, String state,
            PortfolioCalculatedData sp500, PortfolioCalculatedData original,
            PortfolioCalculatedData[] optimized, Portfolio[] portfolioArray, double[][] returnsArray) {
	this.portfolioOptimizationName = portfolioOptimizationName;
	this.state = state;
	this.sp500 = sp500;
	this.original = original;
	this.optimized = optimized;
	this.portfolioArray = portfolioArray;
	this.returnsArray = returnsArray;
    }

    public static String getKeyFieldName() {
	return "portfolioOptimizationName";
    }

    public String getPortfolioOptimizationName() { return portfolioOptimizationName; }
    public String getState()                      { return state; }
    public void   setState(String state)          { this.state = state; }

    public void setSP500(PortfolioCalculatedData sp500)         { this.sp500 = sp500; }
    public void setOriginal(PortfolioCalculatedData original)   { this.original = original; }
    public void setOptimized(PortfolioCalculatedData[] opt)     { this.optimized = opt; }
    public void setPortfolioArray(Portfolio[] portfolioArray)   { this.portfolioArray = portfolioArray; }
    public void setReturnsArray(double[][] returnsArray)        { this.returnsArray = returnsArray; }

    public PortfolioCalculatedData getSP500()           { return sp500; }
    public PortfolioCalculatedData getOriginal()        { return original; }
    public PortfolioCalculatedData[] getOptimized()     { return optimized; }
    public Portfolio[] getPortfolioArray()              { return portfolioArray; }
    public double[][] getReturnsArray()                 { return returnsArray; }

    public static String getCreateTableString(String tableName) {
	String createTableSql="create table "+tableName+" (portfolioOptimizationName varchar(80) not null primary key, state varchar(80) not null,sp500 blob not null, original blob not null, optimized longblob not null, portfolioArray blob not null, returnsArray blob not null);";
	return createTableSql;
    }

    public int hashCode() {
	int result = 17;
	result = 37*result + portfolioOptimizationName.hashCode();
	return result;
    }

    public boolean equals(Object o) {
	if (this == o) return true;
	if (o instanceof PortfolioOptimization) {
	    PortfolioOptimization p = (PortfolioOptimization) o;
	    return (this.portfolioOptimizationName.equals(p.portfolioOptimizationName));
	}
	return false;
    }

    public String toString() {
	return portfolioOptimizationName;
    }

    public PortfolioOptimization deepCopy() {
	String n = new String(portfolioOptimizationName);
	String s = new String(state);
	PortfolioCalculatedData sp = sp500.deepCopy();
	PortfolioCalculatedData o = original.deepCopy();
	PortfolioCalculatedData[] op = new PortfolioCalculatedData[optimized.length];
	System.arraycopy(optimized,0,op,0,optimized.length);
	Portfolio[] pa = new Portfolio[portfolioArray.length];
	for (int i=0;i<pa.length;i++)
	    pa[i] = ((Portfolio)portfolioArray[i]).deepCopy();
	double[][] ra = new double[pa.length][];
	for (int i=0;i<ra.length;i++) {
	    ra[i] = new double[returnsArray[i].length];
	    for (int j=0;j<ra[i].length;j++)
		ra[i][j] = returnsArray[i][j];
	}
	return new PortfolioOptimization(n,s,sp,o,op,pa,ra);
    }
}
