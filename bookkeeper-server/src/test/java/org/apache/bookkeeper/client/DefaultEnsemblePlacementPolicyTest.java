package org.apache.bookkeeper.client;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.apache.bookkeeper.client.BKException.BKNotEnoughBookiesException;
import org.apache.bookkeeper.client.EnsemblePlacementPolicy.PlacementPolicyAdherence;
import org.apache.bookkeeper.client.EnsemblePlacementPolicy.PlacementResult;
import org.apache.bookkeeper.conf.ClientConfiguration;
import org.apache.bookkeeper.feature.FeatureProvider;

import java.util.Set;

import org.apache.bookkeeper.net.BookieId;
import org.apache.bookkeeper.net.DNSToSwitchMapping;
import org.apache.bookkeeper.proto.BookieAddressResolver;
import org.apache.bookkeeper.stats.StatsLogger;

import static org.junit.Assert.assertEquals;

import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.mockito.Mock;

import io.netty.util.HashedWheelTimer;

@RunWith(value=Parameterized.class)
public class DefaultEnsemblePlacementPolicyTest {

    private int ensembleSize;
    private int quorumSize; 
    private int ackQuorumSize;
    private Map<String, byte[]> customMetadata;
    static Map<String, byte[]> paramMetadata = new HashMap<String, byte[]>();
    private int excludeBookies;
    static Set<BookieId> paramExclude;
    private PlacementResult<List<BookieId>> ret;
    private PlacementPolicyAdherence retPolicy;
    private ArrayList<BookieId> newBookies;
    private boolean isWeighted;
    private boolean fill;
    private int knownBookies;
    private DefaultEnsemblePlacementPolicy dEpp;


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

    @Parameters
    public static Collection<Object[]> getTestParameters(){
        return Arrays.asList(new Object[][]{     
            {0, 0, 0,  paramMetadata, 3, 5, false, false}, //Entro nel primo return perchè ensembleSize è 0
            {10, 0, 0,  paramMetadata, 3, 5, true, true}, //Entro in isWeighted e nell'if successivo perchè sottrazione tra allBookies e excludeBookies è minore di ensembleSize
            {10, 0, 0,  paramMetadata, 30, 50, true, true}, //Entro in isWeighted e va a buon fine perchè sottrazione tra allBookies e excludeBookies è maggiore di ensembleSize e knownBookies è pieno
            {10, 0, 0,  paramMetadata, 30, 50, true, false}, //Entro in isWeighted e ho throw perchè knownBookies è vuoto
            {10, 0, 0,  paramMetadata, 30, 50, false, true}, //Entro nell'else e va a buon fine perchè knownBookies è pieno
            {10, 0, 0,  paramMetadata, 30, 50, false, false}, //Entro nell'else e ho throw perchè knownBookies è vuoto
        });
    }

    public DefaultEnsemblePlacementPolicyTest(int ensembleSize, int quorumSize, int ackQuorumSize,
        Map<String, byte[]> customMetadata, int knownBookies, int excludeBookies, boolean isWeighted, boolean fill){
        this.ensembleSize = ensembleSize;
        this.quorumSize = quorumSize; 
        this.ackQuorumSize = ackQuorumSize;
        this.customMetadata = customMetadata;
        this.excludeBookies = excludeBookies;
        this.isWeighted = isWeighted;
        this.knownBookies = knownBookies;
        this.fill = fill;
    }


    @Before
    public void newEnsembleSetUp() throws BKNotEnoughBookiesException, UnknownHostException{
        paramMetadata.put("meta1", "value1".getBytes());
        dEpp = new DefaultEnsemblePlacementPolicy();
        paramExclude = createBookieIds(excludeBookies);

        if(ensembleSize<=0){
            ret = dEpp.newEnsemble(ensembleSize, quorumSize, ackQuorumSize, customMetadata, paramExclude);
            retPolicy = ret.isAdheringToPolicy();
            newBookies = new ArrayList<BookieId>(ensembleSize);
        }
        else if(isWeighted){
            setIsWeighted();
        }
    }

    @Test
    public void newEnsembleTest() throws UnknownHostException, BKNotEnoughBookiesException{
        if(ensembleSize<=0){
            assertEquals(1, retPolicy.getNumVal());
            assertEquals(newBookies, ret.getResult());
        }
        else if(isWeighted & ensembleSize > knownBookies || !fill){
            Assert.assertThrows(BKNotEnoughBookiesException.class, () -> dEpp.newEnsemble(ensembleSize, quorumSize, ackQuorumSize, customMetadata, paramExclude));
        }
        else{
            dEpp.onClusterChanged(createBookieIds(knownBookies), createBookieIds(1));
            ret = dEpp.newEnsemble(ensembleSize, quorumSize, ackQuorumSize, customMetadata, paramExclude);
            retPolicy = ret.isAdheringToPolicy();
            assertEquals(5, retPolicy.getNumVal());
            assertEquals(ensembleSize, ret.getResult().size());
            assertEquals(true, isUnique(ret.getResult()));
        }
    }
    public boolean isUnique(List<BookieId> biList){
        for(BookieId bi : paramExclude){
            if(biList.contains(bi)){
                return false;
            }
        }
        Set<BookieId> unique = new HashSet<>(biList);
        if(unique.size()<biList.size()){
            return false;
        }
        return true;
    }

    public void setIsWeighted(){
        ClientConfiguration conf = new ClientConfiguration();
        conf.setDiskWeightBasedPlacementEnabled(isWeighted);
        dEpp.initialize(conf, optionalDnsResolver, hashedWheelTime, featureProvide, statsLogger, bookieAddressResolver);
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


}
