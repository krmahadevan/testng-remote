package org.testng.remote.strprotocol;


import java.io.IOException;
import java.net.SocketTimeoutException;

import org.testng.TestNGException;

/**
 * Central class to connect to the host and send message.
 *
 * @author Cedric Beust <cedric@beust.com>
 */
public class MessageHub {

  private boolean m_debug = false;

  private IMessageSender m_messageSender;

  public MessageHub(IMessageSender messageSender) {
    m_messageSender = messageSender;
  }

  /**
   * Starts the connection.
   *
   * @throws TestNGException if an exception occurred while establishing the connection
   */
  public void connect() throws IOException {
    m_messageSender.connect();
  }

  /**
   * Shutsdown the connection to the remote test listener.
   */
  public void shutDown() {
    m_messageSender.shutDown();
  }

  public void sendMessage(IMessage message) {
    try {
      m_messageSender.sendMessage(message);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public IMessage receiveMessage() {
    IMessage result = null;
    try {
      result = m_messageSender.receiveMessage();
      m_messageSender.sendAck();
    } catch (Exception e) {
      e.printStackTrace();
    }
    return result;
  }

  public void setDebug(boolean debug) {
    m_debug = debug;
  }

  public void initReceiver() throws SocketTimeoutException {
    m_messageSender.initReceiver();
  }

  public IMessageSender getMessageSender() {
    return m_messageSender;
  }
}
