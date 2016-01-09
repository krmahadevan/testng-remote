package org.testng.remote.support;

import com.google.auto.service.AutoService;
import org.osgi.framework.Version;
import org.osgi.framework.VersionRange;
import org.testng.remote.IRemoteTestNG;

@AutoService(RemoteTestNGFactory.class)
public class RemoteTestNGFactory6_5 implements RemoteTestNGFactory {

    private static final VersionRange RANGE = new VersionRange("[6.5.1,6.8.1)");

    @Override
    public boolean accept(Version version) {
        return version != null && RANGE.includes(version);
    }

    @Override
    public SuiteDispatcherAdapter createSuiteDispatcherAdapter() {
        return new SuiteDispatcherAdapter6_5();
    }

    @Override
    public IRemoteTestNG createRemoteTestNG() {
        return new RemoteTestNG6_5();
    }
}
