package org.testng.remote;

import static org.testng.internal.Utils.defaultIfStringEmpty;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;

import org.testng.CommandLineArgs;
import org.testng.IInvokedMethodListener;
import org.testng.ISuite;
import org.testng.ISuiteListener;
import org.testng.ITestRunnerFactory;
import org.testng.TestNG;
import org.testng.TestNGException;
import org.testng.TestRunner;
import org.testng.collections.Lists;
import org.testng.remote.strprotocol.GenericMessage;
import org.testng.remote.strprotocol.IMessageSender;
import org.testng.remote.strprotocol.MessageHelper;
import org.testng.remote.strprotocol.MessageHub;
import org.testng.remote.strprotocol.RemoteTestListener;
import org.testng.remote.strprotocol.SerializedMessageSender;
import org.testng.remote.strprotocol.StringMessageSender;
import org.testng.remote.strprotocol.SuiteMessage;
import org.testng.reporters.JUnitXMLReporter;
import org.testng.reporters.TestHTMLReporter;
import org.testng.xml.XmlSuite;
import org.testng.xml.XmlTest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Extension of TestNG registering a remote TestListener.
 *
 * @author Cedric Beust <cedric@beust.com>
 */
public class RemoteTestNG extends TestNG {
  private static final String LOCALHOST = "localhost";

  // The following constants are referenced by the Eclipse plug-in, make sure you
  // modify the plug-in as well if you change any of them.
  public static final String DEBUG_PORT = "12345";
  public static final String DEBUG_SUITE_FILE = "testng-customsuite.xml";
  public static final String DEBUG_SUITE_DIRECTORY = System.getProperty("java.io.tmpdir");
  public static final String PROPERTY_DEBUG = "testng.eclipse.debug";
  public static final String PROPERTY_VERBOSE = "testng.eclipse.verbose";
  // End of Eclipse constants.

  private ITestRunnerFactory m_customTestRunnerFactory;
  private String m_host;

  /** Port used for the string protocol */
  private Integer m_port = null;

  /** Port used for the serialized protocol */
  private static Integer m_serPort = null;

  private static boolean m_debug;

  private static boolean m_dontExit;

  private static boolean m_ack;

  public void setHost(String host) {
    m_host = defaultIfStringEmpty(host, LOCALHOST);
  }

  private void calculateAllSuites(List<XmlSuite> suites, List<XmlSuite> outSuites) {
    for (XmlSuite s : suites) {
      outSuites.add(s);
//      calculateAllSuites(s.getChildSuites(), outSuites);
    }
  }

  @Override
  public void run() {
    IMessageSender sender = m_serPort != null
        ? new SerializedMessageSender(m_host, m_serPort, m_ack)
        : new StringMessageSender(m_host, m_port);
    final MessageHub msh = new MessageHub(sender);
    msh.setDebug(isDebug());
    try {
      msh.connect();
      // We couldn't do this until now in debug mode since the .xml file didn't exist yet.
      // Now that we have connected with the Eclipse client, we know that it created the .xml
      // file so we can proceed with the initialization
      initializeSuitesAndJarFile();

      List<XmlSuite> suites = Lists.newArrayList();
      calculateAllSuites(m_suites, suites);
//      System.out.println("Suites: " + m_suites.get(0).getChildSuites().size()
//          + " and:" + suites.get(0).getChildSuites().size());
      if(suites.size() > 0) {

        int testCount= 0;

        for (XmlSuite suite : suites) {
          testCount += suite.getTests().size();
        }

        GenericMessage gm= new GenericMessage(MessageHelper.GENERIC_SUITE_COUNT);
        gm.setSuiteCount(suites.size());
        gm.setTestCount(testCount);
        msh.sendMessage(gm);

        addListener(new RemoteSuiteListener(msh));
        setTestRunnerFactory(new DelegatingTestRunnerFactory(buildTestRunnerFactory(), msh));

//        System.out.println("RemoteTestNG starting");
        super.run();
      }
      else {
        System.err.println("No test suite found. Nothing to run");
      }
    }
    catch(Throwable cause) {
      cause.printStackTrace(System.err);
    }
    finally {
//      System.out.println("RemoteTestNG finishing: " + (getEnd() - getStart()) + " ms");
      msh.shutDown();
      if (! m_debug && ! m_dontExit) {
        System.exit(0);
      }
    }
  }

  /**
   * Override by the plugin if you need to configure differently the <code>TestRunner</code>
   * (usually this is needed if different listeners/reporters are needed).
   * <b>Note</b>: you don't need to worry about the wiring listener, because it is added
   * automatically.
   */
  protected ITestRunnerFactory buildTestRunnerFactory() {
    if(null == m_customTestRunnerFactory) {
      m_customTestRunnerFactory= new ITestRunnerFactory() {
          public TestRunner newTestRunner(ISuite suite, XmlTest xmlTest,
              List<IInvokedMethodListener> listeners) {
            TestRunner runner =
              new TestRunner(getConfiguration(), suite, xmlTest,
                  false /*skipFailedInvocationCounts */,
                  listenerCollectionToArray(listeners));
            if (m_useDefaultListeners) {
              runner.addListener(new TestHTMLReporter());
              runner.addListener(new JUnitXMLReporter());
            }

            return runner;
          }
        };
    }

    return m_customTestRunnerFactory;
  }

  public static void main(String[] args) throws ParameterException {
    CommandLineArgs cla = new CommandLineArgs();
    RemoteArgs ra = new RemoteArgs();
    new JCommander(Arrays.asList(cla, ra), args);
    m_dontExit = ra.dontExit;
    if (cla.port != null && ra.serPort != null) {
      throw new TestNGException("Can only specify one of " + CommandLineArgs.PORT
          + " and " + RemoteArgs.PORT);
    }
    m_debug = cla.debug;
    m_ack = ra.ack;
    if (m_debug) {
//      while (true) {
        initAndRun(args, cla, ra);
//      }
    }
    else {
      initAndRun(args, cla, ra);
    }
  }

  private static void initAndRun(String[] args, CommandLineArgs cla, RemoteArgs ra) {
    RemoteTestNG remoteTestNg = new RemoteTestNG();
    if (m_debug) {
      // In debug mode, override the port and the XML file to a fixed location
      cla.port = Integer.parseInt(DEBUG_PORT);
      ra.serPort = cla.port;
      cla.suiteFiles = Arrays.asList(new String[] {
          DEBUG_SUITE_DIRECTORY + DEBUG_SUITE_FILE
      });
    }
    remoteTestNg.configure(cla);
    remoteTestNg.setHost(cla.host);
    m_serPort = ra.serPort;
    remoteTestNg.m_port = cla.port;
    if (isVerbose()) {
      StringBuilder sb = new StringBuilder("Invoked with ");
      for (String s : args) {
        sb.append(s).append(" ");
      }
      p(sb.toString());
//      remoteTestNg.setVerbose(1);
//    } else {
//      remoteTestNg.setVerbose(0);
    }
    validateCommandLineParameters(cla);
    remoteTestNg.run();
//    if (m_debug) {
//      // Run in a loop if in debug mode so it is possible to run several launches
//      // without having to relauch RemoteTestNG.
//      while (true) {
//        remoteTestNg.run();
//        remoteTestNg.configure(cla);
//      }
//    } else {
//      remoteTestNg.run();
//    }
  }

  private static void p(String s) {
    if (isVerbose()) {
      System.out.println("[RemoteTestNG] " + s);
    }
  }

  public static boolean isVerbose() {
    boolean result = System.getProperty(PROPERTY_VERBOSE) != null || isDebug();
    return result;
  }

  public static boolean isDebug() {
    return m_debug || System.getProperty(PROPERTY_DEBUG) != null;
  }

  private String getHost() {
    return m_host;
  }

  private int getPort() {
    return m_port;
  }

  /** A ISuiteListener wiring the results using the internal string-based protocol. */
  private static class RemoteSuiteListener implements ISuiteListener {
    private final MessageHub m_messageSender;

    RemoteSuiteListener(MessageHub smsh) {
      m_messageSender= smsh;
    }

    @Override
    public void onFinish(ISuite suite) {
      m_messageSender.sendMessage(new SuiteMessage(suite, false /*start*/));
    }

    @Override
    public void onStart(ISuite suite) {
      m_messageSender.sendMessage(new SuiteMessage(suite, true /*start*/));
    }
  }

  private static class DelegatingTestRunnerFactory implements ITestRunnerFactory {
    private final ITestRunnerFactory m_delegateFactory;
    private final MessageHub m_messageSender;

    DelegatingTestRunnerFactory(ITestRunnerFactory trf, MessageHub smsh) {
      m_delegateFactory= trf;
      m_messageSender= smsh;
    }

    public TestRunner newTestRunner(ISuite suite, XmlTest test,
        List<IInvokedMethodListener> listeners) {
      TestRunner tr = m_delegateFactory.newTestRunner(suite, test, listenerCollectionToArray(listeners));
      tr.addListener(new RemoteTestListener(suite, test, m_messageSender));
      return tr;
    }
  }

  private static List<IInvokedMethodListener> listenerCollectionToArray(Collection<IInvokedMethodListener> listeners) {
    // Convert Collection to Set to make sure only register one instance for invoked method listener 
    Set<IInvokedMethodListener> invokedMethodListenerSet = new HashSet<>();
    for (IInvokedMethodListener listener : listeners) {
      invokedMethodListenerSet.add(listener);
    }

    // Convert the Set to List is to be back-compatible with 6.8.x or below
    List<IInvokedMethodListener> invokedMethodListeners = new ArrayList<>();
    for (IInvokedMethodListener listener : invokedMethodListenerSet) {
      invokedMethodListeners.add(listener);
    }

    return invokedMethodListeners;
  }
}
