package org.testng.remote.support;

import org.osgi.framework.VersionRange;
import org.testng.remote.AbstractRemoteTestNGFactory;
import org.testng.remote.IRemoteTestNG;

import com.google.auto.service.AutoService;

@AutoService(RemoteTestNGFactory.class)
public class RemoteTestNGFactory6_0 extends AbstractRemoteTestNGFactory {

  private static final VersionRange RANGE = new VersionRange("[6.0,6.5.1)");

  @Override
  public IRemoteTestNG createRemoteTestNG() {
    return new RemoteTestNG6_0();
  }

  @Override
  protected VersionRange getAcceptableVersions() {
    return RANGE;
  }
}
