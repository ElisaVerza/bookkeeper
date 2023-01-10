package org.apache.bookkeeper.bookie;
import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;

import org.apache.bookkeeper.common.util.Watcher;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;


@RunWith(value=Parameterized.class)
public class FileInfoTest{
    private FileInfo fi;

    private long firstLac;
    private long secondLac;
    private long singleLac;
    
    @Parameters
    public static Collection<Long[]> getTestParameters(){
        return Arrays.asList(new Long[][]{
            {2l, 1l, 1l}, //inserimento lac minore
            {1l, 2l, -1l}, //inserimento lac maggiore
            {1l, 1l, 0l} //inserimento lac maggiore
        });
    }

    public FileInfoTest(long firstLac, long secondLac, long singleLac){
        this.firstLac = firstLac;
        this.secondLac = secondLac;
        this.singleLac = singleLac;
    }

    @Before
    public void setUp() throws IOException {
        //Creazione del ledger index file
        File fl = new File(Variables.LEDGER_FILE_INDEX);

        byte[] mk = Variables.MASTER_KEY.getBytes();
        int version = Variables.VERSION;
        fi = new FileInfo(fl, mk, version);
    }

    @Test
    public void testMultiLac() {
        fi.setLastAddConfirmed(firstLac);
        assertEquals(Math.max(firstLac, secondLac), fi.setLastAddConfirmed(secondLac));
    }

    @Test
    public void testSingleLac() {
        assertEquals(singleLac, fi.setLastAddConfirmed(singleLac));
    }
    
}
