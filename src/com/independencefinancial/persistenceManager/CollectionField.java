package com.bcfinancial.persistenceManager;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

public class CollectionField {
    private static boolean DEBUG=false;
    
    public static CollectionField makeCollectionField(Field field,Object j) {
	if (!persistablePrimitive(field)) {
	    debug("field: "+field+" is not persistable because it is final, static, or transient");
	    return null;
	}
	System.err.println("CollectionFields not supported: \n"+field+"\n"+j);
	return null;
    }
    private static boolean persistablePrimitive(Field field) {
	int modifier = field.getModifiers();
	return ! (Modifier.isFinal(modifier)) &&
	    ! (Modifier.isStatic(modifier)) &&
	    ! (Modifier.isTransient(modifier));
    }  

    private static void debug(String s) {
	if (DEBUG)
	    System.out.println(s);
    }

}

