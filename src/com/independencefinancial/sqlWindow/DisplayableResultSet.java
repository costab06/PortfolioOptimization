package com.bcfinancial.sqlWindow;


import java.sql.*;

class DisplayableResultSet extends Object
{
    ResultSet theResultSet;
    String    theResult;

    public DisplayableResultSet(ResultSet result)
    {
        theResultSet = result;
    }

    public String getString( )
    throws SQLException, NoClassDefFoundError
    {
        theResult = "";

		ResultSetMetaData metaData = theResultSet.getMetaData( );
		int colCount = metaData.getColumnCount( );

		int    colSize  [ ]  = new int    [colCount];
		String colLabel [ ]  = new String [colCount];
		int    colType  [ ]  = new int    [colCount];
		String colTName [ ]  = new String [colCount];
		int    colPrec  [ ]  = new int    [colCount];
		int    colScale [ ]  = new int    [colCount];

	    theResult += "\n";
		for (int i = 1; i <= colCount; i++)
		{
		    colSize [i - 1] = metaData.getColumnDisplaySize(i);
		    colLabel[i - 1] = metaData.getColumnLabel      (i);
		    colType [i - 1] = metaData.getColumnType       (i);
		    colTName[i - 1] = metaData.getColumnTypeName   (i);
		    colPrec [i - 1] = metaData.getPrecision        (i);
		    colScale[i - 1] = metaData.getScale            (i);

		    if (colSize[i - 1] < 1 + colLabel[i - 1].length( ))
		        colSize[i - 1] = 1 + colLabel[i - 1].length( );

		    theResult += rightPad(colLabel[i - 1], colSize[i - 1]);

		}
		theResult += "\n\n";

		int rows = 0;

		while (theResultSet.next( ))
		{
		    rows++;
			for (int i = 1; i <= colCount; i++)
			{
			    String colvalue = theResultSet.getString(i);
			    if (colvalue == null) colvalue = "";
				theResult += rightPad(colvalue, colSize[i - 1]);
			}
			theResult += "\n";
		}

		theResult += "\n(" + rows + " rows included)\n";
		return theResult;
	}

	public String rightPad(String s, int len)
	{
	    int curlen = s.length( );
	    if (curlen > len) return repString("*", len);
	    return s + repString(" ", (len - curlen));
	}

	public String repString(String s, int times)
	{
	    String result = "";
	    for (int i = 0; i < times; i++)
	    {
	        result += s;
	    }
	    return result;
	}
}

