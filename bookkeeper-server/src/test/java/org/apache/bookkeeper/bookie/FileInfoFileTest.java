package org.apache.bookkeeper.bookie;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
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
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(value=Parameterized.class)
public class FileInfoFileTest{
    private FileInfo fi;

    private boolean fileExists;
    private Integer signature;
    private Integer version;
    private Integer lacBufferLen;
    private Integer overflow;
    private static byte[] headerMK = "SecondMK".getBytes();
    private Integer headerMKLen;
    public int remaining;

    
    @Parameters
    public static Collection<Object[]> getTestParameters(){
        return Arrays.asList(new Object[][]{
            {true, ByteBuffer.wrap("BKLE".getBytes(UTF_8)).getInt(), 1, headerMK.length , 16, 0}, //Test in cui viene creato un file con header valido
            {true, ByteBuffer.wrap("NotASignature".getBytes(UTF_8)).getInt(), 1, headerMK.length,16, 0}, //Test signature sbagliata
            {true, ByteBuffer.wrap("BKLE".getBytes(UTF_8)).getInt(), 2, headerMK.length,16, 0}, //Test version header maggiore di 1
            {true, ByteBuffer.wrap("BKLE".getBytes(UTF_8)).getInt(), 1, -1, 16, 0}, //Test MK len negativa
            {true, ByteBuffer.wrap("BKLE".getBytes(UTF_8)).getInt(), 1, headerMK.length+Variables.OVERFLOW_INDEX,16, 0}, //Test Mk len maggiore del buffer
            {true, ByteBuffer.wrap("BKLE".getBytes(UTF_8)).getInt(), 1, headerMK.length, 8, 0}, //Test lac len 10
            {true, ByteBuffer.wrap("BKLE".getBytes(UTF_8)).getInt(), 1, headerMK.length, 16, Variables.OVERFLOW_INDEX}, //Test len lac maggiore del buffer
            {false, null, null, null, null, 0}, //Test in cui non viene creato un file
            {true, null, null, null, null, 0}, //Test in cui non viene creato un file
        });
    }

    public FileInfoFileTest(boolean fileExists, Integer signature, Integer version, Integer headerMKLen, Integer lacBufferLen, Integer overflow){
        this.fileExists = fileExists;
        this.signature = signature;
        this.version = version;
        this.lacBufferLen = lacBufferLen;
        this.headerMKLen = headerMKLen;
        this.overflow = overflow;
        
    }
/*Nel setup viene creato l'oggetto FileInfo */
    @Before
    public void setUp() throws IOException {
        int i = 0;
        File fl = null;

        if(fileExists){
            fl = new File(Variables.LEDGER_FILE_INDEX);
        }

        byte[] mk = Variables.MASTER_KEY.getBytes();
        int ver = Variables.VERSION;
        fi = new FileInfo(fl, mk, ver);
        if(signature!=null & version!=null & headerMKLen != null & lacBufferLen != null){
            //Popolo il file per i test
            FileChannel myWriter = new RandomAccessFile(fl, "rw").getChannel();
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
            headerBB.putInt(1);
            headerBB.putInt(lacBufferLen+overflow);
            headerBB.put(lacBB);
            headerBB.rewind();
            myWriter.position(0);
            myWriter.write(headerBB);
        }
    }

    @Test
    public void readHeaderTest() throws IOException {
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
}

