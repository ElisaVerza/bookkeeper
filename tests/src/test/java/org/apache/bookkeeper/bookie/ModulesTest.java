package org.apache.bookkeeper.bookie;

import org.apache.bookkeeper.bookie.FileInfo;
import org.junit.Test;

public class ModulesTest {

  @Test
  public void integrationTest1() {
    new FileInfo().coveredByIntegrationTest();
  }  
}