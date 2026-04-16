package com.bcfinancial.mapObjects;


import java.util.*;
import java.lang.reflect.*;

public class RetrievalFields {
  private String className;
  private List fieldNames;
  private Map fieldValues;

  public RetrievalFields (String className) {
    this.className = className;
    fieldNames = new ArrayList ();
    fieldValues = new HashMap ();
  }

  public void addField (String field,  Object value) {
    if (fieldValues.containsKey (field)) // actually this won't catch null...
      throw new IllegalArgumentException ("Duplicate field: " + field);
    fieldNames.add (field);
    if (value != null)
      fieldValues.put (field, value);
  }

  public String getClassName () {
    return className;
  }

  public Iterator getFieldNames () {
    return fieldNames.iterator ();
  }

  private static final Map representationClasses = new HashMap ();
  static {
    representationClasses.put (boolean.class, Boolean.class);
    representationClasses.put (byte.class, Byte.class);
    representationClasses.put (short.class, Short.class);
    representationClasses.put (int.class, Integer.class);
    representationClasses.put (long.class, Long.class);
    representationClasses.put (float.class, Float.class);
    representationClasses.put (double.class, Double.class);
    representationClasses.put (boolean.class, Boolean.class);
    representationClasses.put (Date.class, Date.class);
    representationClasses.put (Double.class, Double.class);
    representationClasses.put (Integer.class, Integer.class);
  }

  public Object getValue (Object field, Class type) {
    Object value = fieldValues.get (field);
    if ((value == null) || !(value instanceof String)) {
      return value;
    } else {
      String s = (String) value;
      if (type == String.class) {
        return s;
      } else if (type == char.class) {
        return new Character (s.charAt (0));
      } else {

	  System.out.println("Value is of type other than String or chars");

        try {
          Class clazz = (Class) representationClasses.get (type);

	  System.out.println("Got a class of type: "+clazz+" for type "+type);

          Constructor constructor = clazz.getConstructor (new Class[] { String.class });

	  System.out.println("Got a constructor of type: "+constructor+" which will be called with a: "+s);



          return constructor.newInstance (new Object[] { s });
        } catch (Exception ex) {
          throw new IllegalArgumentException ("Type error: " + type.getName () + ": " + ex);
        }
      }
    }
  }

  public String toString () {
    StringBuffer result = new StringBuffer ("RetrievalFields[");
    result.append ("className=").append (className);
    result.append (",fields={");
    Iterator fields = getFieldNames ();
    while (fields.hasNext ()) {
      Object field = fields.next ();
      result.append (field);
      result.append ('=');
      result.append (fieldValues.get (field));
      if (fields.hasNext ())
        result.append (',');
    }
    result.append ("}]");
    return result.toString ();
  }
}
