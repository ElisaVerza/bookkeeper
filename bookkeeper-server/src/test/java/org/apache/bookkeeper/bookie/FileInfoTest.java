package org.apache.bookkeeper.bookie;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;

import static java.nio.charset.StandardCharsets.UTF_8;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.List;

import org.apache.bookkeeper.common.util.Watcher;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;



@RunWith(Enclosed.class)
public class FileInfoTest {

    @RunWith(value=Parameterized.class)
    public static class FileInfoFileTest{
        private FileInfo fi;
        private File fl;
        private boolean fileExists;
        private Integer signature;
        private Integer version;
        private Integer lacBufferLen;
        private Integer overflow;
        private int statebits;
        private static byte[] headerMK = "SecondMK".getBytes();
        private Integer headerMKLen;
        public int remaining;

        @Parameters
        public static Collection<Object[]> getTestParameters(){
            return Arrays.asList(new Object[][]{
                //Test in cui viene creato un file con header valido per readHeaderTest
                {true, ByteBuffer.wrap("BKLE".getBytes(UTF_8)).getInt(), 1, headerMK.length , 16, 0, 3}, 
                {true, ByteBuffer.wrap("BKLE".getBytes(UTF_8)).getInt(), 1, headerMK.length , 16, 0, 4}, 
                {true, ByteBuffer.wrap("NotASignature".getBytes(UTF_8)).getInt(), 1, headerMK.length,16, 0, 0}, //Test signature sbagliata
                {true, ByteBuffer.wrap("BKLE".getBytes(UTF_8)).getInt(), 2, headerMK.length,16, 0, 0}, //Test version header maggiore di 1
                {true, ByteBuffer.wrap("BKLE".getBytes(UTF_8)).getInt(), 1, -1, 16, 0, 0}, //Test MK len negativa
                {true, ByteBuffer.wrap("BKLE".getBytes(UTF_8)).getInt(), 1, headerMK.length+Variables.OVERFLOW_INDEX,16, 0, 0}, //Test Mk len maggiore del buffer
                {true, ByteBuffer.wrap("BKLE".getBytes(UTF_8)).getInt(), 1, headerMK.length, 8, 0, 0}, //Test lac len 10
                {true, ByteBuffer.wrap("BKLE".getBytes(UTF_8)).getInt(), 1, headerMK.length, 16, Variables.OVERFLOW_INDEX, 0}, //Test len lac maggiore del buffer
                {true, null, null, null, null, 0, 0}, //Test in cui non viene creato un file
                {false, null, null, null, null, 0, 0}, //Test in cui non viene creato un file
            });
        }

        public FileInfoFileTest(boolean fileExists, Integer signature, Integer version, Integer headerMKLen, 
                                Integer lacBufferLen, Integer overflow, int statebits){
            this.fileExists = fileExists;
            this.signature = signature;
            this.version = version;
            this.lacBufferLen = lacBufferLen;
            this.headerMKLen = headerMKLen;
            this.overflow = overflow;
            this.statebits = statebits;

        }

    /*Nel setup viene creato l'oggetto FileInfo. Viene passato l'oggetto File solo se il test lo prevede e viene costruito l'header
    * secondo i valori richiesti dal singolo test parametrizzato.
    */
        @Before
        public void setUp() throws IOException {
            byte[] mk = Variables.MASTER_KEY.getBytes();;
            fl = new File(Variables.LEDGER_FILE_INDEX);
            int ver = Variables.VERSION;
            fi = new FileInfo(fl, mk, ver);
            
            if(signature!=null & version!=null & headerMKLen != null & lacBufferLen != null){
                remaining = populateFile(fl, lacBufferLen, headerMK, signature, version, headerMKLen, statebits, overflow);
            }
        }

        @Test
        public void readHeaderTest() throws IOException {
            if(!fileExists){
                deleteFile(Variables.LEDGER_FILE_INDEX);
            }
            if(!fileExists||(signature==null & version==null & headerMKLen == null & lacBufferLen == null)||
                signature!=ByteBuffer.wrap("BKLE".getBytes(UTF_8)).getInt()||version<0||version>1||headerMKLen<0||
                headerMKLen>remaining||(lacBufferLen+overflow<16 & lacBufferLen+overflow!=0)||lacBufferLen+overflow>lacBufferLen){
                Assert.assertThrows(Exception.class, () -> fi.readHeader());
            }
            else{
                fi.readHeader();
                byte[] retMK =  fi.masterKey;
                String retMKString = new String(retMK, StandardCharsets.UTF_8);
                String MKString = new String(headerMK, StandardCharsets.UTF_8);
                assertEquals(MKString, retMKString);
            }
        }

        @Test
        public void setFencedTest() throws IOException{
            if(statebits!=0){
                fi.readHeader();
                if(statebits%2==0){
                    assertEquals(true, fi.setFenced());
                }
                else{
                    assertEquals(false, fi.setFenced());
                }    
            }
        }
    }

    @RunWith(value=Parameterized.class)
    public static class FileInfoUtilityTest{
    private FileInfo fi;
    private FileInfo spyFi;

    private long firstLac;
    private long secondLac;
    private long singleLac;
    private int buffSize;
    private int bbLen;
    private long position;
    private long offset;
    private String[] byteToWrite;
    static String[] singleStr = {"Write something"};
    static String[] multiStr = {"Write something", "and something else"};
    private boolean bestEffort;
    private Boolean isClosed;
    private Boolean force;
    private ByteBuffer[] bbArray;
    private int writtenChar;
    private ByteBuffer toWrite;

    private ByteBuffer bufferToRead;

    @Mock
    Watcher<LastAddConfirmedUpdateNotification> watcher;
    
    @Parameters
    public static Collection<Object[]> getTestParameters(){
        return Arrays.asList(new Object[][]{     
            {2l, 1l, 1l, 8, 0, false, singleStr[0].length()+100, singleStr, true, false, 0}, //inserimento lac minore, besteffort false con buffer in cui copiare più grande della sringa, scrivo da inizio file
            {1l, 1l, 0l, 24, singleStr[0].length()+100, true, singleStr[0].length(), singleStr, true, true, -2000}, //inserimento lac maggiore, indice maggiore lunghezza buffer, inizio a scrivere da indice negativo
            {1l, 2l, -1l, 16, -2000, true, singleStr[0].length(), singleStr, false, false, 0}, //inserimento lac maggiore, indice negativo
            {1l, 1l, 0l, 24, 0, false, singleStr[0].length(), multiStr, null, null, 1000}, //inserimento lac maggiore, besteffort false con buffer in cui copiare più piccolo della stringa da copiare, scrivo da un certo punto del file
            //{1l, 1l, 0l, 24, 0, true, 0, null, null, null, 0}, //inserimento lac maggiore, file channel senza dati scritti
        });
    }

    public FileInfoUtilityTest(long firstLac, long secondLac, long singleLac, int buffSize, long offset, boolean bestEffort, int bbLen, String byteToWrite[], Boolean force, Boolean isClosed, long position){
        this.firstLac = firstLac;
        this.secondLac = secondLac;
        this.singleLac = singleLac;
        this.buffSize = buffSize;
        this.offset = offset;
        this.bestEffort = bestEffort;
        this.bbLen = bbLen;
        this.byteToWrite = byteToWrite;
        this.force = force;
        this.isClosed = isClosed;
        this.position = position;
    }
/*Nel setup viene creato l'oggetto FileInfo e inizializzati gli oggetti mockati infine viene popolato l'header del file*/
    @Before
    public void setUp() throws IOException {
        MockitoAnnotations.openMocks(this);
        byte[] headerMK = "SecondMK".getBytes();

        File fl = new File(Variables.LEDGER_FILE_INDEX);

        byte[] mk = Variables.MASTER_KEY.getBytes();
        int version = Variables.VERSION;
        fi = new FileInfo(fl, mk, version);
        //Scrittura sul file dell'header
        populateFile(fl, 16, headerMK, ByteBuffer.wrap("BKLE".getBytes(UTF_8)).getInt(), version, headerMK.length, 1, 0);
        spyFi = spy(fi);
    }

    @Before
    public void readWritebf(){
        toWrite = ByteBuffer.allocate(0);
        bbArray = new ByteBuffer[0];
        writtenChar = 0;
        int i = 0;
        if(byteToWrite!=null){
            bufferToRead = ByteBuffer.allocate(bbLen);
            bbArray = new ByteBuffer[byteToWrite.length];
            for(i=0; i<byteToWrite.length; i++){
                writtenChar += byteToWrite[i].length();
                toWrite = ByteBuffer.allocate(byteToWrite[i].length()); 
                toWrite.put(ByteBuffer.wrap(byteToWrite[i].getBytes(UTF_8)));
                toWrite.rewind();
                bbArray[i] = toWrite;   
            }
        }
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
        public void writeTest() throws IOException{
            if(position >= 0){
                assertEquals(writtenChar, fi.write(bbArray, position));
            }
            else if(position < 0){
                Assert.assertThrows(Exception.class, () -> fi.write(bbArray, position));   
            }
        }

        @Test
        public void readTest() throws IOException{

            fi.write(bbArray, 0);

            if(offset>writtenChar){
                assertEquals(0, fi.read(bufferToRead, offset, bestEffort));     
            }
            if(bestEffort & offset<writtenChar & offset>=0){
                assertEquals(toWrite.capacity(), fi.read(bufferToRead, offset, bestEffort));
            }
            else if(!bestEffort & bbLen <= toWrite.capacity()){
                assertEquals(bbLen, fi.read(bufferToRead, offset, bestEffort));         
            }
            else if((!bestEffort & bbLen > toWrite.capacity())|| offset<0){
                Assert.assertThrows(Exception.class, () -> fi.read(bufferToRead, offset, bestEffort));
            }
        }

        @Test
        public void closeTest() throws IOException{
            if(isClosed!=null && force!=null){
                spyFi.close(force);
                if(isClosed){
                    spyFi.close(force);
                    verify(spyFi, times(1)).notifyWatchers(LastAddConfirmedUpdateNotification.FUNC, Long.MAX_VALUE);
                }
                else if(force){
                    verify(spyFi, times(1)).flushHeader();
                    verify(spyFi, times(1)).notifyWatchers(LastAddConfirmedUpdateNotification.FUNC, Long.MAX_VALUE);
                }
                else{
                    verify(spyFi, times(0)).flushHeader();
                    verify(spyFi, times(1)).notifyWatchers(LastAddConfirmedUpdateNotification.FUNC, Long.MAX_VALUE);
                }
            }

        }
    }

    public static int populateFile(File fl, Integer lacBufferLen, byte[] headerMK, int signature, int version, int headerMKLen, int statebits, int overflow) throws FileNotFoundException{
        int i = 0;
        int remaining = 0;
        //Popolo il file per i test
        try (
        FileChannel myWriter = new RandomAccessFile(fl, "rw").getChannel()) {
            ByteBuffer lacBB = ByteBuffer.allocate(lacBufferLen);
            while(i<(lacBufferLen/8)){
                lacBB.putLong(1l);
                i++;
            }
            lacBB.rewind();
            ByteBuffer headerBB = ByteBuffer.allocate(20+lacBufferLen+headerMK.length);
            
            headerBB.putInt(signature);
            headerBB.putInt(version);
            headerBB.putInt(headerMKLen);
            remaining = headerBB.remaining();
            headerBB.put(headerMK);
            headerBB.putInt(statebits);
            headerBB.putInt(lacBufferLen+overflow);
            headerBB.put(lacBB);
            headerBB.rewind();
            myWriter.position(0);
            myWriter.write(headerBB);
        } catch (FileNotFoundException e) {
            throw e;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return remaining;
    }

    public static boolean deleteFile(String filename){
        File myObj = new File(Variables.LEDGER_FILE_INDEX); 
        if (myObj.delete()) { 
        return true;
        } else {
        return false;
        } 
    }


}
