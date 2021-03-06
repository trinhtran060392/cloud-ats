/**
 * 
 */
package org.ats.services.generator;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.ats.common.PageList;
import org.ats.common.StringUtil;
import org.ats.service.BlobModule;
import org.ats.service.blob.BlobService;
import org.ats.services.DataDrivenModule;
import org.ats.services.GeneratorModule;
import org.ats.services.KeywordServiceModule;
import org.ats.services.OrganizationContext;
import org.ats.services.OrganizationServiceModule;
import org.ats.services.PerformanceServiceModule;
import org.ats.services.data.DatabaseModule;
import org.ats.services.data.MongoDBService;
import org.ats.services.event.EventModule;
import org.ats.services.event.EventService;
import org.ats.services.keyword.Case;
import org.ats.services.keyword.CaseFactory;
import org.ats.services.keyword.CaseReference;
import org.ats.services.keyword.CaseService;
import org.ats.services.keyword.KeywordProject;
import org.ats.services.keyword.KeywordProjectFactory;
import org.ats.services.keyword.KeywordProjectService;
import org.ats.services.keyword.Suite;
import org.ats.services.keyword.SuiteFactory;
import org.ats.services.keyword.SuiteReference;
import org.ats.services.keyword.SuiteService;
import org.ats.services.organization.base.AuthenticationService;
import org.ats.services.organization.entity.Space;
import org.ats.services.organization.entity.Tenant;
import org.ats.services.organization.entity.User;
import org.ats.services.organization.entity.fatory.ReferenceFactory;
import org.ats.services.organization.event.AbstractEventTestCase;
import org.ats.services.performance.JMeterFactory;
import org.ats.services.performance.JMeterSampler;
import org.ats.services.performance.JMeterScript;
import org.ats.services.performance.JMeterScriptReference;
import org.ats.services.performance.JMeterScriptService;
import org.ats.services.performance.PerformanceProject;
import org.ats.services.performance.PerformanceProjectFactory;
import org.ats.services.performance.PerformanceProjectService;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Guice;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import com.mongodb.gridfs.GridFSInputFile;

/**
 * @author <a href="mailto:haithanh0809@gmail.com">Nguyen Thanh Hai</a>
 *
 * Jul 14, 2015
 */
public class GeneratorTestCase  extends AbstractEventTestCase {
  
  private AuthenticationService<User> authService;
  private OrganizationContext context;
  
  private Tenant tenant;
  private Space space;
  private User user;
  
  private PerformanceProjectFactory perfFactory;
  private PerformanceProjectService perfService;
  private ReferenceFactory<JMeterScriptReference> jmeterScriptRef;
  private JMeterScriptService  jmeterService;
  
  private KeywordProjectService keywordProjectService;
  private KeywordProjectFactory keywordProjectFactory;
  private SuiteService suiteService;
  private SuiteFactory suiteFactory;
  private ReferenceFactory<SuiteReference> suiteRefFactory;
  private CaseFactory caseFactory;
  private CaseService caseService;
  private ReferenceFactory<CaseReference> caseRefFactory;
  
  private GeneratorService generetorService;
  private BlobService blobService;

  @BeforeClass
  public void init() throws Exception {
    this.injector = Guice.createInjector(
        new DatabaseModule(), 
        new EventModule(),
        new OrganizationServiceModule(),
        new DataDrivenModule(),
        new KeywordServiceModule(),
        new PerformanceServiceModule(),
        new GeneratorModule(),
        new BlobModule());
    
    this.mongoService = injector.getInstance(MongoDBService.class);
    this.mongoService.dropDatabase();
    
    this.authService = injector.getInstance(Key.get(new TypeLiteral<AuthenticationService<User>>(){}));
    this.context = injector.getInstance(OrganizationContext.class);
    
    //performance
    this.perfFactory = injector.getInstance(PerformanceProjectFactory.class);
    this.perfService = injector.getInstance(PerformanceProjectService.class);
    this.jmeterScriptRef = this.injector.getInstance(Key.get(new TypeLiteral<ReferenceFactory<JMeterScriptReference>>(){}));
    this.jmeterService = this.injector.getInstance(JMeterScriptService.class);
    
    //keyword
    this.keywordProjectService = injector.getInstance(KeywordProjectService.class);
    this.keywordProjectFactory = injector.getInstance(KeywordProjectFactory.class);
    
    this.suiteService = injector.getInstance(SuiteService.class);
    this.suiteFactory = injector.getInstance(SuiteFactory.class);
    this.suiteRefFactory = injector.getInstance(Key.get(new TypeLiteral<ReferenceFactory<SuiteReference>>(){}));
    
    this.caseService = injector.getInstance(CaseService.class);
    this.caseFactory = injector.getInstance(CaseFactory.class);
    this.caseRefFactory = injector.getInstance(Key.get(new TypeLiteral<ReferenceFactory<CaseReference>>(){}));
    
    this.generetorService = this.injector.getInstance(GeneratorService.class);
    this.blobService = this.injector.getInstance(BlobService.class);
    
    //start event service
    this.eventService = injector.getInstance(EventService.class);
    this.eventService.setInjector(injector);
    this.eventService.start();
    
    initService();
  }
  
  @AfterClass
  public void shutdown() throws Exception {
    this.mongoService.dropDatabase();
  }

  
  @BeforeMethod
  public void setup() throws Exception {
    this.tenant = tenantFactory.create("Fsoft");
    this.tenantService.create(this.tenant);

    this.space = spaceFactory.create("FSU1.BU11");
    this.space.setTenant(tenantRefFactory.create(this.tenant.getId()));
    this.spaceService.create(this.space);

    this.user = userFactory.create("haint@cloud-ats.net", "Hai", "Nguyen");
    this.user.setTenant(tenantRefFactory.create(this.tenant.getId()));
    this.user.joinSpace(spaceRefFactory.create(this.space.getId()));
    this.user.setPassword("12345");
    this.userService.create(this.user);
    
    this.authService.logIn("haint@cloud-ats.net", "12345");
    this.spaceService.goTo(spaceRefFactory.create(this.space.getId()));
  }
  
  
  @AfterMethod
  public void tearDown() {
    this.authService.logOut();
    this.mongoService.dropDatabase();
  }
  
  @Test
  public void testGeneratePerformanceProject() throws IOException {

    PerformanceProject project = perfFactory.create("Test Performance", "");
    
    JMeterFactory factory = new JMeterFactory();
    JMeterSampler loginPost = factory.createHttpPost("Login codeproject post", 
        "https://www.codeproject.com:443/script/Membership/LogOn.aspx?rp=%2f%3floginkey%3dfalse",
        "kakalot", 0,
        factory.createArgument("FormName", "MenuBarForm"),
        factory.createArgument("Email", "kakalot8x08@gmail.com"),
        factory.createArgument("Password", "tititi"));
    
    JMeterSampler gotoArticle = factory.createHttpGet("Go to top article", 
        "http://www.codeproject.com/script/Articles/TopArticles.aspx?ta_so=5",
        null, 0);
    
    JMeterScript loginScript = factory.createJmeterScript(
        "Login",
        1, 20, 5, false, 0, project.getId(), "haint@cloudats.net",
        loginPost);
    loginScript.setNumberEngines(2);
    jmeterService.create(loginScript);
    
    JMeterScript gotoArticleScript = factory.createJmeterScript(
        "GotoArticle", 1, 20, 5, false, 0, project.getId(), "haint@cloudats.net", gotoArticle);
    gotoArticleScript.setNumberEngines(1);
    jmeterService.create(gotoArticleScript);
    
    JMeterScript rawScript = factory.createRawJmeterScript(project.getId(), "haint@cloudats.net", "rawUpload",
        StringUtil.readStream(new FileInputStream("src/test/resources/multiple_thread_group.jmx")));
    
    rawScript.setNumberEngines(1);
    rawScript.setNumberThreads(10);
    rawScript.setRamUp(10);
    rawScript.setLoops(2);
    jmeterService.create(rawScript);
    
    JMeterScript rawCsvScript = factory.createRawJmeterScript(project.getId(), "rawUpload","haint@cloudats.net",
        StringUtil.readStream(new FileInputStream("src/test/resources/multiple_thread_group.jmx")));
    
    rawCsvScript.setNumberEngines(1);
    rawCsvScript.setNumberThreads(10);
    rawCsvScript.setRamUp(10);
    rawCsvScript.setLoops(2);
    jmeterService.create(rawCsvScript);
    
    GridFSInputFile file1 = blobService.create(new File("src/test/resources/level.csv"));
    file1.put("script_id", rawCsvScript.getId());
    file1.save();
    
    GridFSInputFile file2 = blobService.create(new File("src/test/resources/LoginEinavi.csv"));
    file2.put("script_id", rawCsvScript.getId());
    file2.save();
    
    perfService.create(project);
    
    PageList<JMeterScript> pages = jmeterService.getJmeterScripts(project.getId());
    
    List<JMeterScript> list ;
    List<JMeterScriptReference> listRef = new ArrayList<JMeterScriptReference>();
    while (pages.hasNext()) {
      list = pages.next();
      
      for (JMeterScript script : list) {
         listRef.add(jmeterScriptRef.create(script.getId()));
      }
    }
    Assert.assertEquals(generetorService.generatePerformance("target/perf",  project.getId().substring(0, 8), true, listRef), 
        "target/perf/" + project.getId().substring(0, 8) + ".zip");
  }
  
  @Test
  public void testGenerateKeywordProjectWithVersionSelenium() throws IOException {
    
    KeywordProject project = keywordProjectFactory.create("Full Example", "");
    
    ObjectMapper m = new ObjectMapper();
    JsonNode rootNode = m.readTree(new File("src/test/resources/full_example.json"));
    JsonNode stepsNode = rootNode.get("steps");
    
    List<CaseReference> cases = new ArrayList<CaseReference>();
    Case caze = caseFactory.create(project.getId(), "test", null, "haint@cloudats.net");
    for (JsonNode json : stepsNode) {
      caze.addAction(json);
    }
    caseService.create(caze);
    cases.add(caseRefFactory.create(caze.getId()));

    Suite fullExampleSuite= suiteFactory.create(project.getId(), "FullExample", SuiteFactory.DEFAULT_INIT_DRIVER, cases, context.getUser().getEmail());
    suiteService.create(fullExampleSuite);
    
    rootNode = m.readTree(new File("src/test/resources/acceptAlert.json"));
    stepsNode = rootNode.get("steps");
    cases.clear();
    
    caze = caseFactory.create(project.getId(), "test", null, "haint@cloudats.net");
    for (JsonNode json : stepsNode) {
      caze.addAction(json);
    }
    caseService.create(caze);
    cases.add(caseRefFactory.create(caze.getId()));
    
    Suite acceptAlertSuite = suiteFactory.create(project.getId(), "AcceptAlert", SuiteFactory.DEFAULT_INIT_DRIVER, cases, context.getUser().getEmail());
    suiteService.create(acceptAlertSuite);
    
    project.setVersionSelenium("2.47.1");
    keywordProjectService.create(project);
    
    Assert.assertEquals(
        generetorService.generateKeyword("target/fk",  project.getId().substring(0, 8), true, Arrays.<SuiteReference>asList(
            suiteRefFactory.create(fullExampleSuite.getId()), 
            suiteRefFactory.create(acceptAlertSuite.getId())), 2, project.getVersionSelenium()), 
        "target/fk/" + project.getId().substring(0, 8) + ".zip");
    
    Assert.assertTrue(new File("target/fk/" + project.getId().substring(0, 8) + "/src/test/java/org/ats/generated/FullExample.java").exists());
    Assert.assertTrue(new File("target/fk/" + project.getId().substring(0, 8) + "/src/test/java/org/ats/generated/AcceptAlert.java").exists());
    Assert.assertTrue(new File("target/fk/" + project.getId().substring(0, 8) +"/pom.xml").exists());
  }
  
  @Test
  public void testGenerateKeywordProject() throws IOException {
    
    KeywordProject project = keywordProjectFactory.create("Full Example", "");
    
    ObjectMapper m = new ObjectMapper();
    JsonNode rootNode = m.readTree(new File("src/test/resources/full_example.json"));
    JsonNode stepsNode = rootNode.get("steps");
    
    List<CaseReference> cases = new ArrayList<CaseReference>();
    Case caze = caseFactory.create(project.getId(), "test", null, "haint@cloudats.net");
    for (JsonNode json : stepsNode) {
      caze.addAction(json);
    }
    caseService.create(caze);
    cases.add(caseRefFactory.create(caze.getId()));

    Suite fullExampleSuite= suiteFactory.create(project.getId(), "FullExample", SuiteFactory.DEFAULT_INIT_DRIVER, cases, context.getUser().getEmail());
    suiteService.create(fullExampleSuite);
    
    rootNode = m.readTree(new File("src/test/resources/acceptAlert.json"));
    stepsNode = rootNode.get("steps");
    cases.clear();
    
    caze = caseFactory.create(project.getId(), "test", null, "haint@cloudats.net");
    for (JsonNode json : stepsNode) {
      caze.addAction(json);
    }
    caseService.create(caze);
    cases.add(caseRefFactory.create(caze.getId()));
    
    Suite acceptAlertSuite = suiteFactory.create(project.getId(), "AcceptAlert", SuiteFactory.DEFAULT_INIT_DRIVER, cases, context.getUser().getEmail());
    suiteService.create(acceptAlertSuite);
    
    keywordProjectService.create(project);
    
    Assert.assertEquals(
        generetorService.generateKeyword("target/fk",  project.getId().substring(0, 8), true, Arrays.<SuiteReference>asList(
            suiteRefFactory.create(fullExampleSuite.getId()), 
            suiteRefFactory.create(acceptAlertSuite.getId()))), 
        "target/fk/" + project.getId().substring(0, 8) + ".zip");
    
    Assert.assertTrue(new File("target/fk/" + project.getId().substring(0, 8) + "/src/test/java/org/ats/generated/FullExample.java").exists());
    Assert.assertTrue(new File("target/fk/" + project.getId().substring(0, 8) + "/src/test/java/org/ats/generated/AcceptAlert.java").exists());
  }
  
  @Test
  public void testGenerateKeywordProjectWithOptions() throws IOException {
    
    KeywordProject project = keywordProjectFactory.create("Full Example", "");
    
    project.setValueDelay(3);
    project.setVersionSelenium("2.45.0");
    
    ObjectMapper m = new ObjectMapper();
    JsonNode rootNode = m.readTree(new File("src/test/resources/full_example.json"));
    JsonNode stepsNode = rootNode.get("steps");
    
    List<CaseReference> cases = new ArrayList<CaseReference>();
    Case caze = caseFactory.create(project.getId(), "test", null, "haint@cloudats.net");
    for (JsonNode json : stepsNode) {
      caze.addAction(json);
    }
    caseService.create(caze);
    cases.add(caseRefFactory.create(caze.getId()));

    Suite fullExampleSuite= suiteFactory.create(project.getId(), "FullExampleWithOptions", SuiteFactory.DEFAULT_INIT_DRIVER, cases, context.getUser().getEmail());
    suiteService.create(fullExampleSuite);
    
    rootNode = m.readTree(new File("src/test/resources/acceptAlert.json"));
    stepsNode = rootNode.get("steps");
    cases.clear();
    
    caze = caseFactory.create(project.getId(), "test", null, "haint@cloudats.net");
    for (JsonNode json : stepsNode) {
      caze.addAction(json);
    }
    caseService.create(caze);
    cases.add(caseRefFactory.create(caze.getId()));
    
    Suite acceptAlertSuite = suiteFactory.create(project.getId(), "AcceptAlertWithOptions", SuiteFactory.DEFAULT_INIT_DRIVER, cases, context.getUser().getEmail());
    suiteService.create(acceptAlertSuite);
    
    keywordProjectService.create(project);
    
    Assert.assertEquals(
        generetorService.generateKeyword("target/fk",  project.getId().substring(0, 8), true, Arrays.<SuiteReference>asList(
            suiteRefFactory.create(fullExampleSuite.getId()), 
            suiteRefFactory.create(acceptAlertSuite.getId())),
             project.getValueDelay(),project.getVersionSelenium()), 
        "target/fk/" + project.getId().substring(0, 8) + ".zip");
    
    Assert.assertTrue(new File("target/fk/" + project.getId().substring(0, 8) + "/src/test/java/org/ats/generated/FullExampleWithOptions.java").exists());
    Assert.assertTrue(new File("target/fk/" + project.getId().substring(0, 8) + "/src/test/java/org/ats/generated/AcceptAlertWithOptions.java").exists());
  }
  
  @Test
  public void testGenerateKeywordProjectWithBrowserVersion() throws IOException {
    KeywordProject project = keywordProjectFactory.create("Full Example", "");
    
    ObjectMapper m = new ObjectMapper();
    JsonNode rootNode = m.readTree(new File("src/test/resources/full_example.json"));
    JsonNode stepsNode = rootNode.get("steps");
    
    List<CaseReference> cases = new ArrayList<CaseReference>();
    Case caze = caseFactory.create(project.getId(), "test", null, "haint@cloudats.net");
    for (JsonNode json : stepsNode) {
      caze.addAction(json);
    }
    caseService.create(caze);
    cases.add(caseRefFactory.create(caze.getId()));
    
    String initGoogleDriver = "System.setProperty(\"webdriver.chrome.driver\", \"/home/haint/chromedriver\");\n wd = new ChromeDriver();";

    Suite fullExampleSuite= suiteFactory.create(project.getId(), "FullExample", initGoogleDriver, cases, context.getUser().getEmail());
    suiteService.create(fullExampleSuite);
    
    rootNode = m.readTree(new File("src/test/resources/acceptAlert.json"));
    stepsNode = rootNode.get("steps");
    cases.clear();
    
    caze = caseFactory.create(project.getId(), "test", null, "haint@cloudats.net");
    for (JsonNode json : stepsNode) {
      caze.addAction(json);
    }
    
    rootNode = m.readTree(new File("src/test/resources/custom.json"));
    stepsNode = rootNode.get("steps");
    for (JsonNode json : stepsNode) {
      caze.addAction(json);
    }
    
    caseService.create(caze);
    cases.add(caseRefFactory.create(caze.getId()));
    
    String initFireFoxDriverWithVersion = "System.setProperty(\"webdriver.firefox.bin\", \"/home/haint/data/firefox-41.0.2/firefox\");\n" +
    "wd = new FirefoxDriver();";
    
    Suite acceptAlertSuite = suiteFactory.create(project.getId(), "AcceptAlert", initFireFoxDriverWithVersion,  cases, context.getUser().getEmail());
    suiteService.create(acceptAlertSuite);
    
    keywordProjectService.create(project);
    
    Assert.assertEquals(
        generetorService.generateKeyword("target/fk",  project.getId().substring(0, 8), true, Arrays.<SuiteReference>asList(
            suiteRefFactory.create(fullExampleSuite.getId()), 
            suiteRefFactory.create(acceptAlertSuite.getId()))), 
        "target/fk/" + project.getId().substring(0, 8) + ".zip");
    
    Assert.assertTrue(new File("target/fk/" + project.getId().substring(0, 8) + "/src/test/java/org/ats/generated/FullExample.java").exists());
    Assert.assertTrue(new File("target/fk/" + project.getId().substring(0, 8) + "/src/test/java/org/ats/generated/AcceptAlert.java").exists());
  }
}
