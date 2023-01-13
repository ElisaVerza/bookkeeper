package org.apache.bookkeeper.bookie;
import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

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

import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;

import static java.nio.charset.StandardCharsets.UTF_8;


@RunWith(value=Parameterized.class)
public class FileInfoUtilityTest{
    private FileInfo fi;

    private long firstLac;
    private long secondLac;
    private long singleLac;
    private int buffSize;
    private long offset;
    private String byteToWrite;
    static String str = "Write something";
    private boolean bestEffort;
    private int size;

    private ByteBuffer bufferToRead;

    @Mock
    Watcher<LastAddConfirmedUpdateNotification> watcher;
    
    @Parameters
    public static Collection<Object[]> getTestParameters(){
        return Arrays.asList(new Object[][]{
            {2l, 1l, 1l, 8, 0, true, str, str.length()}, //inserimento lac minore, besteffort true e size del buffer giusta
            {1l, 2l, -1l, 16, 0, false, str, str.length()+100}, //inserimento lac maggiore, besteffort false e size del buffaer maggiore
            {1l, 1l, 0l, 24, str.length()+100, true, str, str.length()}, //inserimento lac maggiore, indice maggiore lunghezza buffer
            {1l, 1l, 0l, 24, -2000, true, str, str.length()}, //inserimento lac maggiore, indice negativo
            {1l, 1l, 0l, 24, 0, true, "", 1}, //inserimento lac maggiore, file channel senza dati scritti*/
        });
    }

    public FileInfoUtilityTest(long firstLac, long secondLac, long singleLac, int buffSize, long offset, boolean bestEffort, String byteToWrite, int size){
        this.firstLac = firstLac;
        this.secondLac = secondLac;
        this.singleLac = singleLac;
        this.buffSize = buffSize;
        this.offset = offset;
        this.bestEffort = bestEffort;
        this.byteToWrite = byteToWrite;
        this.size = size;
    }
/*Nel setup viene creato l'oggetto FileInfo e inizializzati gli oggetti mockati infine viene popolato l'header del file*/
    @Before
    public void setUp() throws IOException {
        MockitoAnnotations.openMocks(this);
        int i = 0;
        byte[] headerMK = "SecondMK".getBytes();
        bufferToRead = ByteBuffer.allocate(1024);

        File fl = new File(Variables.LEDGER_FILE_INDEX);

        byte[] mk = Variables.MASTER_KEY.getBytes();
        int version = Variables.VERSION;
        fi = new FileInfo(fl, mk, version);

        //Scrittura sul file dell'header
        FileChannel myWriter = new RandomAccessFile(fl, "rw").getChannel();
        ByteBuffer lacBB = ByteBuffer.allocate(16);
        while(i<2){
            lacBB.putLong(1l);
            i++;
        }
        lacBB.rewind();
        ByteBuffer headerBB = ByteBuffer.allocate(36+headerMK.length);
        
        headerBB.putInt(ByteBuffer.wrap("BKLE".getBytes(UTF_8)).getInt());
        headerBB.putInt(1);
        headerBB.putInt(headerMK.length);
        headerBB.put(headerMK);
        headerBB.putInt(1);
        headerBB.putInt(16);
        headerBB.put(lacBB);
        headerBB.rewind();

        myWriter.position(0);

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

/*set e get explicit lac*/
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

            ByteBuf ret = fi.getExplicitLac();
            ByteBuffer lac = ByteBuffer.allocate(ret.capacity());
            ret.readBytes(lac);
            lac.rewind();
            assertEquals(bb, lac);
        }
    }

    @Test
    public void readTest() throws IOException{
        ByteBuffer toWrite = ByteBuffer.allocate(size); 
        toWrite.put(ByteBuffer.wrap(byteToWrite.getBytes(UTF_8)));
        toWrite.rewind();
        ByteBuffer[] bbArray = new ByteBuffer[1];
        bbArray[0] = toWrite;
        fi.write(bbArray, 0);

        if(offset>size || str.length()==0){
            assertEquals(0, fi.read(bufferToRead, offset, bestEffort));     
        }
        else if(bestEffort & offset<size & offset>0){
            assertEquals(toWrite.capacity(), fi.read(bufferToRead, offset, bestEffort));
        }
        else if((!bestEffort)|| offset<0){
            Assert.assertThrows(Exception.class, () -> fi.read(bufferToRead, offset, bestEffort));
        }
    }
}



    //TODO: test da eliminare.
    /*@Test
    public void testSetExplicitLac(){
        int i = 0;
        ByteBuffer bb = ByteBuffer.allocate(16);
        while(i<bb.limit()/2){
            bb.putChar('o');
            i++;
        }
        bb.rewind();

        ByteBuf retLac = Unpooled.buffer(bb.capacity());
        bb.rewind();
        retLac.writeBytes(bb);
        bb.rewind();
        fi.setExplicitLac(retLac);
        int num = 3;
        assertEquals(3, num);
        //Assert.assertThrows(Exception.class, () -> fi.setExplicitLac(retLac));
    }*/
