 

package org.ats.generated;

import java.text.ParseException;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;
import static org.testng.Assert.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.testng.annotations.DataProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.TimeUnit;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.io.File;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.ie.InternetExplorerDriver;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.*;
import static org.openqa.selenium.OutputType.*;

public class AcceptAlert {

  private RemoteWebDriver wd;
  
  @BeforeClass
  public void beforeClass() throws Exception {
  System.out.println("[Start][Suite]{\"name\": \"AcceptAlert\", \"id\": \"1065cdce-0a00-4de4-be7f-26b2a4761687\", \"jobId\" : \"\", \"timestamp\": \""+System.currentTimeMillis()+"\"}");
  }
   
  @AfterClass
  public void afterClass() throws Exception {
  System.out.println("[End][Suite]{\"name\": \"AcceptAlert\", \"id\": \"1065cdce-0a00-4de4-be7f-26b2a4761687\", \"jobId\" : \"\", \"timestamp\": \""+System.currentTimeMillis()+"\"}");
  }

  @BeforeMethod
  public void setUp() throws Exception {
    wd = new FirefoxDriver();
    wd.manage().timeouts().implicitlyWait(60, TimeUnit.SECONDS);
    wd.manage().window().maximize();
  }
   
  @AfterMethod
  public void tearDown() {
    wd.quit();
  }
  
  
  @Test
  public void testadfeab39() throws Exception {
    System.out.println("[Start][Case]{\"name\": \"test\", \"id\": \"adfeab39-881e-4a1c-b2df-fef217a3d08f\", \"timestamp\": \""+System.currentTimeMillis()+"\"} "); 

    System.out.println("[Start][Step]{\"keyword_type\":\"get \",\"url\":\"http://seleniumbuilder.github.io/se-builder/test/confirm.html\",\"timestamp\": \""+System.currentTimeMillis()+"\",\"params\":[\"url\"]} "); 
    try {
wd.get("http://seleniumbuilder.github.io/se-builder/test/confirm.html");
System.out.println("[End][Step]");
} catch (Exception e) {
wd.getScreenshotAs(FILE).renameTo(new File("target/error_"+System.currentTimeMillis()+"_get.png"));
e.printStackTrace();
throw e ;
}


    System.out.println("[Start][Step]{\"keyword_type\":\"acceptAlert \",\"timestamp\": \""+System.currentTimeMillis()+"\",\"params\":[]} "); 
    try { 
    wd.switchTo().alert().accept();
    System.out.println("[End][Step]"); 
   } catch (Exception e) { 
     wd.getScreenshotAs(FILE).renameTo(new File("target/error_"+System.currentTimeMillis()+"_acceptAlert.png"));
     e.printStackTrace();
     throw e ; 
   }


    System.out.println("[Start][Step]{\"keyword_type\":\"assertAlertText \",\"text\":\"yes\",\"timestamp\": \""+System.currentTimeMillis()+"\",\"params\":[\"text\"]} "); 
    try {
assertEquals( wd.switchTo().alert().getText(), "yes");
System.out.println("[End][Step]");
} catch (AssertionError ae) {
wd.getScreenshotAs(FILE).renameTo(new File("target/_error"+System.currentTimeMillis()+"_assertAlertText.png"));
ae.printStackTrace();
throw ae ;
}

    System.out.println("[End][Case]"); 
  }

  public static boolean isAlertPresent(RemoteWebDriver wd) {
    try {
      wd.switchTo().alert();
      return true;
    } catch (NoAlertPresentException e) {
      return false;
    }
  }
}