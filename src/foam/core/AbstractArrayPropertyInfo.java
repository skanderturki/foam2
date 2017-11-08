/**
 * @license
 * Copyright 2017 The FOAM Authors. All Rights Reserved.
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package foam.core;

import foam.dao.pg.IndexedPreparedStatement;
import foam.nanos.logger.Logger;
import java.lang.UnsupportedOperationException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public abstract class AbstractArrayPropertyInfo
  extends AbstractPropertyInfo
{
  @Override
  public void setFromString(Object obj, String value) {
    if ( value == null ) {
      this.set(obj, null);
      return;
    }
    // TODO: TO REUSE THIS LIST WITH A THREADLOCAL FOR BETTER PERFORMANCE
    List<String> list = new LinkedList<String>();
    StringBuilder sb = new StringBuilder(); 
    char prev = '$';
    int length = value.length();
    char[] cs = value.toCharArray();
    for ( int i = 0 ; i < cs.length ; i++ ) {
      if ( cs[i] == '\\' ) {
        if ( prev == '\\' ) {
          sb.append("\\");
          prev = '$';
        } else {
          prev = '\\';
        }
      } else if ( cs[i] == ',' ) {
        if ( prev == '\\' ) {
          sb.append(',');
        } else {
          list.add(sb.toString());
          sb.setLength(0);
        }
        prev = '$';
      } else {
        sb.append(cs[i]);
        prev = cs[i];
      }
    }
    list.add(sb.toString());
    int resultSize = list.size();
    String[] result = new String[resultSize];
    //add support for other array types
    this.set(obj, list.toArray(result));
  }

  public abstract String of();

  // NESTED ARRAY
  @Override
  public Object fromXML(X x, XMLStreamReader reader) {
    List objList = new ArrayList();
    String startTag = reader.getLocalName();
    try {
      int eventType;
      while ( reader.hasNext() ) {
        eventType = reader.next();
        switch ( eventType ) {
          case XMLStreamConstants.START_ELEMENT:
            if ( reader.getLocalName().equals("value") ) {
              // TODO: TYPE CASTING FOR PROPER CONVERSION. NEED FURTHER SUPPORT FOR PRIMITIVE TYPES
              throw new UnsupportedOperationException("Primitive typed array XML reading is not supported yet");
            }
            break;
          case XMLStreamConstants.END_ELEMENT:
            if ( reader.getLocalName() == startTag ) { return objList.toArray(); }
        }
      }
    } catch (XMLStreamException ex) {
      Logger logger = (Logger) x.get("logger");
      logger.error("Premature end of XML file");
    }
    return objList.toArray();
  }

  @Override
  public void toXML (FObject obj, Document doc, Element objElement) {
    if ( this.f(obj) == null ) return;

    Element prop = doc.createElement(this.getName());
    objElement.appendChild(prop);

    Object[] nestObj = (Object[]) this.f(obj);
    for ( int k = 0; k < nestObj.length; k++ ) {
      Element nestedProp = doc.createElement("value");
      nestedProp.appendChild(doc.createTextNode(nestObj[k].toString()));
      prop.appendChild(nestedProp);
    }
  }

  @Override
  public void setStatementValue(IndexedPreparedStatement stmt, FObject o) throws java.sql.SQLException {
    Object obj = this.get(o);
    if ( obj == null ) {
      stmt.setObject(null);
      return;
    }
    Object[] os = (Object[]) obj;
    StringBuilder sb = new StringBuilder();
    int length = os.length;
    if ( length == 0 ) {
      stmt.setObject(null);
      return;
    }
    for ( int i = 0 ; i < length ; i++ ) {
      if ( os[i] == null )
        sb.append("");
      else {
        escapeCommasAndAppend(sb, os[i]);
      }
      if ( i < length - 1 ) {
        sb.append(",");
      }
    }
    stmt.setObject(sb.toString());
  }

  @Override
  public void setFromResultSet(java.sql.ResultSet resultSet, int index, FObject o) throws java.sql.SQLException {
    String value = (String) resultSet.getObject(index);
    setFromString(o, value);
  }

  private void escapeCommasAndAppend(StringBuilder builder, Object o) {
    String s = o.toString();
    //replace backslash to double backslash
    s = s.replace("\\", "\\\\");
    //replace comma to backslash+comma
    s = s.replace(",", "\\,");
    builder.append(s);
  }
}
