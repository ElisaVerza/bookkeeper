package org.apache.bookkeeper.bookie;
import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Random;

import org.apache.bookkeeper.common.util.Watcher;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;


@RunWith(value=Parameterized.class)
public class FileInfoTest{
    private FileInfo fi;

    private long firstLac;
    private long secondLac;
    private long singleLac;
    private int buffSize;

    @Mock
    Watcher<LastAddConfirmedUpdateNotification> watcher;
    
    @Parameters
    public static Collection<Object[]> getTestParameters(){
        return Arrays.asList(new Object[][]{
            {2l, 1l, 1l, 8}, //inserimento lac minore
            {1l, 2l, -1l, 16}, //inserimento lac maggiore
            {1l, 1l, 0l, 24} //inserimento lac maggiore
        });
    }

    public FileInfoTest(long firstLac, long secondLac, long singleLac, int buffSize){
        this.firstLac = firstLac;
        this.secondLac = secondLac;
        this.singleLac = singleLac;
        this.buffSize = buffSize;
    }
/*Nel setup viene creato l'oggetto FileInfo e vengono inizializzati gli oggetti mockati*/
    @Before
    public void setUp() throws IOException {
        MockitoAnnotations.openMocks(this);

        File fl = new File(Variables.LEDGER_FILE_INDEX);

        byte[] mk = Variables.MASTER_KEY.getBytes();
        int version = Variables.VERSION;
        fi = new FileInfo(fl, mk, version);
    }
/*Seguono due test per il metodo setLastAddConfirmed. Il metodo prende in input un long il cui valore rappresenta
 * l'indice last add confirmed (lac) ed in output il long con il lac aggiornato. Qualsiasi tipo di long è valido, 
 * ma il comportamento atteso è che venga rispettato un ordinamento, ovvero il lac viene aggiornato solo se è 
 * maggiore del precedente. Quindi in questo test viene provato l'inserimeto di due lac con valore atteso il massimo 
 * tra i due inserimenti.
*/
    @Test
    public void testMultiLac() {
        fi.setLastAddConfirmed(firstLac);
        assertEquals(Math.max(firstLac, secondLac), fi.setLastAddConfirmed(secondLac));
    }

/*In questo secondo test viene effettuato il set di un long positivo, negativo o zero. Essendo il parametro di input un 
 *long non è accettato un input null.
*/
    @Test
    public void testSingleLac() {
        assertEquals(singleLac, fi.setLastAddConfirmed(singleLac));
    }

/*Test per il metodo waitForLastAddConfirmedUpdate. Prende in input un long il cui valore rappresenta il lac precedente e 
 * un oggetto Watcher<LastAddConfirmedUpdateNotification> che è stato mockato e ritorna un booleano posto a true se il 
 * lac attuale è minonre del lac precedente, altrimenti false. Viene prima effettuato l'inserimento di un lac e poi testato
 * il metodo con un previousLac minore, maggiore o uguale a quello attuale.
 */
    @Test
    public void testLacWait(){
        boolean wait = true;
        if(secondLac > firstLac){wait = false;}
        fi.setLastAddConfirmed(secondLac);
        assertEquals(wait, fi.waitForLastAddConfirmedUpdate(firstLac, watcher));
    }

/**/
    @Test
    public void testSetExplicitLacLen(){
        int i = 0;
        long entry;
        long leftLimit = -100L;
        long rightLimit = 100L;
        List<Long> entryList = new ArrayList<>();
        ByteBuffer bb = ByteBuffer.allocate(buffSize);
        while(i<(buffSize/8)){
            entry = leftLimit + (long) (Math.random() * (rightLimit - leftLimit));
            entryList.add(entry);
            
            bb.putLong(entry);
            i++;
        }
        bb.rewind();

        ByteBuf retLac = Unpooled.buffer(bb.capacity());
        bb.rewind();
        retLac.writeBytes(bb);
        bb.rewind();
        if(buffSize<16){
            Assert.assertThrows(Exception.class, () -> fi.setExplicitLac(retLac));
        }
        else{
            fi.setExplicitLac(retLac);
            assertEquals(entryList.get(1), fi.getLastAddConfirmed());
        }
    }
}
