/**
 * 
 */
package org.ats.services.functional.action;

import java.io.IOException;

import org.ats.services.functional.locator.ILocator;
import org.rythmengine.Rythm;

/**
 * @author <a href="mailto:haithanh0809@gmail.com">Nguyen Thanh Hai</a>
 *
 * Apr 13, 2015
 */
public class StoreElementPresent implements IAction {

  private ILocator locator;
  
  private String variable;
  
  public StoreElementPresent(ILocator locator, String variable) {
    this.locator = locator;
    this.variable = variable;
  }
  
  public String transform() throws IOException {
    StringBuilder sb = new StringBuilder("boolean ").append(variable);
    sb.append(" = (wd.findElements(@locator).size() != 0);\n");
    return Rythm.render(sb.toString(), locator.transform());
  }

  public String getAction() {
    return "storeElementPresent";
  }
}
  