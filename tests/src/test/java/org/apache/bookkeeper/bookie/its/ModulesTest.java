package org.apache.bookkeeper.bookie.its;

import org.apache.bookkeeper.bookie.Module1;
import org.junit.Test;

public class ModulesTest {

  @Test
  public void integrationTest1() {
    new Module1().coveredByIntegrationTest();
  }  
}
