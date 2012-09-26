package test

import org.scalatest.{GivenWhenThen, FeatureSpec}

import tools.Environment
import org.scalatest.matchers.ShouldMatchers

class EnvironmentFeatureTest extends FeatureSpec with GivenWhenThen with ShouldMatchers {

  feature("Environment"){

    scenario("Reading the STAGE property"){

      given("I load the Guardian environment properties")
      val env = new Environment("/etc/gu/install_vars")

      then("The 'stage' configuration should be set to the current environment")
      env.getProperty("STAGE", "unknown") should be ("dev")
    }

    scenario("Missing properties"){

      given("I load the Guardian environment properties")
      val env = new Environment("/etc/gu/missing_conf")
      
      and("the desired configuration is missing")
      
      then("The property  should be set to 'unknown'")
      env.getProperty("foo", "unknown") should be ("unknown")
    }

  }
}



