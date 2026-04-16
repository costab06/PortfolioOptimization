package com.bcfinancial.portfolio;

import java.util.Date;
import com.bcfinancial.data.converter.DoubleArrayConverter;
import com.bcfinancial.data.converter.StringArrayConverter;
import com.bcfinancial.persistenceManager.Persistable;
import jakarta.persistence.*;

@Entity
@Table(name = "portfolios")
@Access(AccessType.FIELD)
public class Portfolio extends Persistable {

    @Id
    @Column(name = "portfolioName", length = 255, nullable = false)
    private String portfolioName;

    @Convert(converter = StringArrayConverter.class)
    @Column(name = "optimizationNames", length = 8192)
    private String[] optimizationNames;

    @Convert(converter = StringArrayConverter.class)
    @Column(name = "symbols", length = 4096, nullable = false)
    private String[] symbols;

    @Convert(converter = DoubleArrayConverter.class)
    @Column(name = "weights", length = 4096, nullable = false)
    private Double[] weights;

    public Portfolio() {} // required by JPA

    public Portfolio (String portfolioName, String[] symbols, Double[] weights) {
	this.portfolioName = portfolioName;
	this.symbols = symbols;
	this.weights = weights;
    }

    public static String getKeyFieldName() {
	return "portfolioName";
    }

    public String getPortfolioName() {
	return portfolioName;
    }

    public void setPortfolioName(String name) {
	this.portfolioName = name;
    }

    public String[] getOptimizationNames() {
	return optimizationNames;
    }

    public void setOptimizationNames(String optimizationName) {
	this.optimizationNames = optimizationNames;
    }

    public void addOptimization(String optimizationName) {
	if (optimizationNames == null) {
	    optimizationNames = new String[1];
	    optimizationNames[0] = optimizationName;
	} else {
	    String[] t = new String[optimizationNames.length+1];
	    System.arraycopy(optimizationNames,0,t,0,optimizationNames.length);
	    t[optimizationNames.length] = optimizationName;
	    optimizationNames = t;
	}
    }

    public void setSymbols (String[] symbols) {
	this.symbols = symbols;
    }

    public void setWeights (Double[] weights) {
	this.weights = weights;
    }

    public String[] getSymbols() {
	return symbols;
    }

    public Double[] getWeights() {
	return weights;
    }

    public static String getCreateTableString(String tableName) {
	String createTableSql="create table "+tableName+" (portfolioName varchar(80) not null primary key, optimizationNames varbinary(10240), symbols varbinary(10240) not null, weights varbinary(10240) not null);";
	return createTableSql;
    }

    public int hashCode() {
	int result = 17;
	result = 37*result + portfolioName.hashCode();
	return result;
    }

    public boolean equals(Object o) {
	if (this == o)
	    return true;
	if (o instanceof Portfolio) {
	    Portfolio p = (Portfolio) o;
	    return (this.portfolioName.equals(p.portfolioName));
	} else
	    return false;
    }

    public String toString() {
	String ret = new String("\n");
	double total = 0.0;
	if (symbols != null && weights != null) {
	    for(int i=0;i<symbols.length;i++) {
		ret+=symbols[i]+"  "+weights[i]+"\n";
		total += ((Double)weights[i]).doubleValue();
	    }
	}
	ret+="Total: "+(total *100)+"%";
	return ret;
    }

    public Portfolio deepCopy() {
	String n = new String(portfolioName);
	String[] s = new String[symbols.length];
	Double[] w = new Double[weights.length];
	for (int i=0;i<symbols.length;i++) {
	    s[i] = new String(symbols[i]);
	    w[i] = new Double(weights[i]);
	}
	return new Portfolio(n,s,w);
    }
}
