/*
 * JBoss, Home of Professional Open Source
 * Copyright 2009, Red Hat Middleware LLC, and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.arquillian.framework.spock;

import java.util.logging.Logger;

import org.jboss.arquillian.test.spi.TestRunnerAdaptor;
import org.jboss.arquillian.test.spi.TestRunnerAdaptorBuilder;
import org.spockframework.runtime.AbstractRunListener;
import org.spockframework.runtime.extension.IGlobalExtension;
import org.spockframework.runtime.model.FeatureInfo;
import org.spockframework.runtime.model.SpecInfo;

/**
 * Arquillian extension to the Spock test framework.
 *
 * @author <a href="mailto:aslak@redhat.com">Aslak Knutsen</a>
 * @version $Revision: $
 */
public class ArquillianExtension implements IGlobalExtension
{
   private static ThreadLocal<TestRunnerAdaptor> deployableTest = new ThreadLocal<TestRunnerAdaptor>();
   
   private static ThreadLocal<SpecInfo> lastCreatedSpec = new ThreadLocal<SpecInfo>();

   private Logger log = Logger.getLogger(ArquillianExtension.class.getName());

   /* (non-Javadoc)
    * @see org.spockframework.runtime.extension.IGlobalExtension#visitSpec(org.spockframework.runtime.model.SpecInfo)
    */
   public void visitSpec(SpecInfo spec)
   {
      // Only create the first time
      if(deployableTest.get() == null)   
      {
         // FIXME resolve configuration
         // Configuration configuration = new XmlConfigurationBuilder().build();
         TestRunnerAdaptor adaptor = TestRunnerAdaptorBuilder.build();
         try 
         {
            log.fine("beforeSuite");
            // don't set it if beforeSuite fails
            adaptor.beforeSuite();
            deployableTest.set(adaptor);
         } 
         catch (Exception e) 
         {
            throw new RuntimeException(e);
         }
      }
      
      ArquillianInterceptor interceptor = new ArquillianInterceptor(deployableTest.get());
      
      final SpecInfo topSpec = spec.getTopSpec();
      topSpec.getSetupSpecMethod().addInterceptor(interceptor);
      topSpec.getSetupMethod().addInterceptor(interceptor);

      // add Interceptors to all feature methods
      for(FeatureInfo feature : topSpec.getAllFeatures())
      {
         feature.getFeatureMethod().addInterceptor(interceptor);         
      }
      
      topSpec.getCleanupMethod().addInterceptor(interceptor);
      topSpec.getCleanupSpecMethod().addInterceptor(interceptor);

      // set the last created Spec, so we can call AfterSuite only when this is done.
      lastCreatedSpec.set(topSpec);
      topSpec.addListener(new AbstractRunListener() 
      {
         @Override
         public void afterSpec(SpecInfo spec)
         {
            if(spec == lastCreatedSpec.get())
            {
               try 
               {
                  log.fine("afterSuite");
                  deployableTest.get().afterSuite();
                  deployableTest.set(null);
                  deployableTest.remove();
               } 
               catch (Exception e) 
               {
                  throw new RuntimeException(e);
               }
            }
         }
      });
   }

}
