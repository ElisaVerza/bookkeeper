package org.apache.bookkeeper.client;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.verify;

import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

import org.apache.bookkeeper.conf.ClientConfiguration;
import org.apache.bookkeeper.feature.FeatureProvider;
import org.apache.bookkeeper.net.BookieId;
import org.apache.bookkeeper.net.DNSToSwitchMapping;
import org.apache.bookkeeper.proto.BookieAddressResolver;
import org.apache.bookkeeper.stats.StatsLogger;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;


import io.netty.util.HashedWheelTimer;

@RunWith(PowerMockRunner.class)
@PowerMockIgnore("javax.management.*")
@PrepareForTest({ DefaultEnsemblePlacementPolicy.class })
public class onChangesTest {

    @Mock
    WeightedRandomSelectionImpl<BookieId> weightedSelection;

    @Mock
    Optional<DNSToSwitchMapping> optionalDnsResolver;
    @Mock
    HashedWheelTimer hashedWheelTime;
    @Mock
    FeatureProvider featureProvide;
    @Mock
    StatsLogger statsLogger;
    @Mock
    BookieAddressResolver bookieAddressResolver;

    @Test
    public void testPrintName() throws Exception {
        PowerMockito.whenNew( WeightedRandomSelectionImpl.class).withAnyArguments().thenReturn(weightedSelection);
        DefaultEnsemblePlacementPolicy dEpp = new DefaultEnsemblePlacementPolicy();
        setIsWeighted(dEpp);
        dEpp.onClusterChanged(createBookieIds(1), createBookieIds(1));

        verify(weightedSelection).updateMap(anyMap());
    }
    public Set<BookieId> createBookieIds(int num) throws UnknownHostException{
        Set<BookieId> toRet = new HashSet<BookieId>();
        for(int i = 0; i<num; i++){
            toRet.add(BookieId.parse(randomStr()));
        }
        return toRet;
    }
    public String randomStr() {
        int leftLimit = 97; // letter 'a'
        int rightLimit = 122; // letter 'z'
        int targetStringLength = 7;
        Random random = new Random();
        StringBuilder buffer = new StringBuilder(targetStringLength);
        for (int i = 0; i < targetStringLength; i++) {
            int randomLimitedInt = leftLimit + (int) 
            (random.nextFloat() * (rightLimit - leftLimit + 1));
            buffer.append((char) randomLimitedInt);
        }
        return buffer.toString();
    }
    public void setIsWeighted(DefaultEnsemblePlacementPolicy dEpp){
        ClientConfiguration conf = new ClientConfiguration();
        conf.setDiskWeightBasedPlacementEnabled(true);
        dEpp.initialize(conf, optionalDnsResolver, hashedWheelTime, featureProvide, statsLogger, bookieAddressResolver);
    }
}
