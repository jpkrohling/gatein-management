/*
 * JBoss, a division of Red Hat
 * Copyright 2010, Red Hat Middleware, LLC, and individual
 * contributors as indicated by the @authors tag. See the
 * copyright.txt in the distribution for a full listing of
 * individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.gatein.management.pomdata.xi;

import org.exoplatform.portal.pom.data.NavigationData;
import org.exoplatform.portal.pom.data.PageData;
import org.exoplatform.portal.pom.data.PortalData;
import org.gatein.management.pomdata.client.api.PomDataClient;
import org.kohsuke.args4j.Option;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.Scanner;

/**
 * @author <a href="mailto:nscavell@redhat.com">Nick Scavelli</a>
 * @version $Revision$
 */
public class Exporter
{
   @Option(name = "--config", aliases = "-c", usage = "Sets configuration file for export", metaVar = " ")
   File configFile;

   @Option(name = "--basedirectory", aliases = "-basedir", usage = "Sets base directory for export", metaVar = " ")
   File basedir;

   @Option(name = "--username", aliases = "-u", usage = "Username to connect to portal with.", metaVar = " ")
   String username;

   @Option(name = "--password", aliases = "-p", usage = "Password to connect to portal with.", metaVar = " ")
   String password;

   @Option(name = "--host", aliases = "-h", usage = "Host of the server the portal is running on.", metaVar = " ")
   String host;

   @Option(name = "--port", usage = "Port of the server the portal is running on.", metaVar = " ")
   int port;

   @Option(name = "--portalcontainer", aliases = "-pc", usage = "Portal container name (ie portal)", metaVar = " ")
   String portalContainer;

   @Option(name = "--scope", aliases = "-s", usage = "Scope of data (ie portal, group, user). Use * for all scopes.", metaVar = " ")
   String scope;

   @Option(name = "--ownerid", aliases = "-o", usage = "Owner id (ie classic, /platform/administrators, root). Use * for all ownerId's.", metaVar = " ")
   String ownerId;

   @Option(name = "--datatype", aliases = "-d", usage = "Data type (ie site, page, navigation). Use * for all data types.", metaVar = " ")
   String dataType;

   @Option(name = "--name", aliases = "-n", usage = "Name of page name or navigation path or * for all pages/navigations.", metaVar = " ")
   String itemName;

   @Option(name = "--help")
   boolean help;

   private PomDataClient client;
   private File exportDir;
   private int level;

   void init(Properties properties) throws Exception
   {
      Date date = Calendar.getInstance().getTime();
      SimpleDateFormat sdf1 = new SimpleDateFormat("yyyy-MM-dd");
      SimpleDateFormat sdf2 = new SimpleDateFormat("HH.mm.ss.SSS");
      String exportPath = new StringBuilder().append(sdf1.format(date)).
         append("/").append(sdf2.format(date)).toString();

      if (basedir == null)
      {
         exportDir = new File("epp-exports", exportPath);
      }
      else
      {
         exportDir = new File(basedir, exportPath);
      }
      if (!exportDir.mkdirs())
      {
         throw new RuntimeException("Could not create export directory " + exportDir.getAbsolutePath());
      }

      System.out.println("Using directory " + exportDir.getAbsolutePath() + " for export.");
   }

   public void doExport() throws Exception
   {
      //TODO: Verify containerName is correct (requires remote call)
      if (portalContainer == null)
      {
         portalContainer = getUserInput("Container name (ie portal)");
      }

      client = PomDataClient.Factory.create(InetAddress.getByName(host), port, portalContainer);

      // Process scopes for export
      Scope[] scopes = getScopes();
      for (Scope s : scopes)
      {
         processScope(s);
      }
   }

   private Scope[] getScopes()
   {
      if (scope == null)
      {
         // Ask user for scope value
         scope = getUserInput("Scope (portal, group, user, * for all)");
      }

      // Parse scope value
      if ("*".equals(scope))
      {
         return new Scope[]{Scope.PORTAL, Scope.GROUP, Scope.USER};
      }
      else
      {
         return new Scope[]{Scope.valueOf(scope.toUpperCase())};
      }
   }
   private String[] getOwnerIds(Scope scope, String inputString)
   {
      if (ownerId == null)
      {
         ownerId = getUserInput(inputString);
         if (ownerId.contains(","))
         {
            return ownerId.split(",");
         }
      }
      if ("*".equals(ownerId))
      {
         Collection<String> ownerIds;
         switch (scope)
         {
            case PORTAL:
               ownerIds = getSiteNames("portal");
               return ownerIds.toArray(new String[ownerIds.size()]);
            case GROUP:
               ownerIds = getSiteNames("group");
               return ownerIds.toArray(new String[ownerIds.size()]);
            case USER:
               ownerIds = getSiteNames("user");
               return ownerIds.toArray(new String[ownerIds.size()]);
            default:
               throw new RuntimeException("Scope " + scope + " not supported.");
         }
      }
      else
      {
         return new String[]{ownerId};
      }
   }

   private Collection<String> getSiteNames(String ownerType)
   {
      List<PortalData> list = client.getPortalConfig(ownerType);
      Collection<String> names = new ArrayList<String>(list.size());
      for (PortalData data : list)
      {
         names.add(data.getName());
      }

      return names;
   }


   private DataType[] getDataTypes()
   {
      if (dataType == null)
      {
         // Ask use for data type value
         dataType = getUserInput("Data Type (site, page, navigation, * for all)");
      }

      if ("*".equals(dataType))
      {
         return new DataType[]{DataType.SITE, DataType.PAGE, DataType.NAVIGATION};
      }
      else
      {
         return new DataType[]{DataType.valueOf(dataType.toUpperCase())};
      }
   }

   private void processScope(Scope scope)
   {
      System.out.println("--- Current scope '" + scope + "' ---");
      level++;

      String[] ownerIds;
      switch (scope)
      {
         case PORTAL:
            ownerIds = getOwnerIds(scope, "Owner Id (site name)");
            break;
         case GROUP:
            ownerIds = getOwnerIds(scope, "Owner Id (group name)");
            break;
         case USER:
            ownerIds = getOwnerIds(scope, "Owner Id (user name)");
            break;
         default:
            throw new RuntimeException("Scope " + scope + " not supported.");
      }

      for (String ownerId : ownerIds)
      {
         indent();
         System.out.print("--- Current owner id '" + ownerId + "' ---\n");
         level++;
         DataType[] datatypes = getDataTypes();
         for (DataType datatype : datatypes)
         {
            level++;
            indent();
            System.out.print("--- Current data type '" + datatype + "' ---\n");
            processExport(scope, ownerId, datatype);
            level--;
         }
         level--;
      }

      level--;
   }

   private void processExport(Scope scope, String ownerId, DataType dataType)
   {
      try
      {
         File currentDir = exportDir;
         switch (scope)
         {
            case PORTAL:
            {
               File dataDir = createDataDir(currentDir, "portal", ownerId);
               writeData(scope, ownerId, dataType, dataDir);
               break;
            }
            case GROUP:
            {
               File dataDir = createDataDir(currentDir, "group", ownerId);
               writeData(scope, ownerId, dataType, dataDir);
               break;
            }
            case USER:
            {
               File dataDir = createDataDir(currentDir, "user", ownerId);
               writeData(scope, ownerId, dataType, dataDir);
               break;
            }
            default:
               throw new RuntimeException("Unsupported scope " + scope);
         }
      }
      catch (IOException ioex)
      {
         throw new RuntimeException("IOException while exporting scope " + scope + " and ownerId " + ownerId + " and data type " + dataType);
      }
   }

   //TODO: Refactor this since we refactored client interface (a lot of dup code)
   private void writeData(Scope scope, String ownerId, DataType dataType, File dataDir) throws IOException
   {
      switch (dataType)
      {
         case SITE:
         {
            PortalData data;
            switch (scope)
            {
               case PORTAL:
                  data = client.getPortalConfig("portal", ownerId);
                  break;
               case GROUP:
                  data = client.getPortalConfig("group", ownerId);
                  break;
               case USER:
                  data = client.getPortalConfig("user", ownerId);
                  break;
               default:
                  throw new RuntimeException("Scope " + scope + " not supported");
            }
            indent();
            if (data != null)
            {
               File xml = new File(dataDir, "portal.xml");
               System.out.println("Exporting site to " + xml.getAbsolutePath());
               client.exportToFile(data, xml);
            }
            else
            {
               System.out.println("Nothing to export.");
            }
            break;
         }
         case PAGE:
         {
            String name = (itemName == null) ? getUserInput("Page name ('*' for all)") : itemName;
            List<PageData> pages = null;
            PageData page = null;
            switch (scope)
            {
               case PORTAL:
                  if ("*".equals(name))
                  {
                     pages = client.getPages("portal", ownerId);
                  }
                  else
                  {
                     page = client.getPage("portal", ownerId, name);
                  }
                  break;
               case GROUP:
                  if ("*".equals(name))
                  {
                     pages = client.getPages("group", ownerId);
                  }
                  else
                  {
                     page = client.getPage("group", ownerId, name);
                  }
                  break;
               case USER:
                  if ("*".equals(name))
                  {
                     pages = client.getPages("user", ownerId);
                  }
                  else
                  {
                     page = client.getPage("user", ownerId, name);
                  }
                  break;
               default:
                  throw new RuntimeException("Scope " + scope + " not supported");
            }
            indent();
            if (page == null && pages == null)
            {
               System.out.println("Nothing to export.");
            }
            else
            {
               File xml = new File(dataDir, "pages.xml");
               System.out.println("Exporting page(s) to " + xml.getAbsolutePath());
               if (pages != null)
               {
                  client.exportToFile(pages, xml);
               }
               else
               {
                  client.exportToFile(page, xml);
               }
            }
            break;
         }
         case NAVIGATION:
         {
            String name = (itemName == null) ? getUserInput("Navigation path ('*' for all)") : itemName;
            NavigationData data;
            switch (scope)
            {
               case PORTAL:
                  if ("*".equals(name))
                  {
                     data = client.getNavigation("portal", ownerId);
                  }
                  else
                  {
                     data = client.getNavigation("portal", ownerId, name);
                  }
                  break;
               case GROUP:
                  if ("*".equals(name))
                  {
                     data = client.getNavigation("group", ownerId);
                  }
                  else
                  {
                     data = client.getNavigation("group", ownerId, name);
                  }
                  break;
               case USER:
                  if ("*".equals(name))
                  {
                     data = client.getNavigation("user", ownerId);
                  }
                  else
                  {
                     data = client.getNavigation("user", ownerId, name);
                  }
                  break;
               default:
                  throw new RuntimeException("Scope " + scope + " not supported");
            }
            indent();
            if (data == null)
            {
               System.out.println("Nothing to export.");
            }
            else
            {
               File xml = new File(dataDir, "navigation.xml");
               System.out.println("Exporting navigation(s) to " + xml.getAbsolutePath());
               client.exportToFile(data, xml);
            }
            break;
         }
         default:
            throw new RuntimeException("Unsupported data type " + dataType);
      }
   }

   private File createDataDir(File parent, String scope, String ownerId) throws IOException
   {
      File cd = new File(parent, scope);
      if (!cd.exists())
      {
         if (!cd.mkdir())
         {
            throw new RuntimeException("Could not create directory " + cd.getAbsolutePath());
         }
      }
      cd = new File(cd, ownerId);
      if (!cd.exists())
      {
         if (!cd.mkdirs())
         {
            throw new RuntimeException("Could not create directory " + cd.getAbsolutePath());
         }
      }
      return cd;
   }

   private void writeBytesToFile(File file, byte[] bytes) throws IOException
   {
      if (bytes == null) return;

      BufferedOutputStream bos = null;
      try
      {
         FileOutputStream fos = new FileOutputStream(file);
         bos = new BufferedOutputStream(fos);
         bos.write(bytes);
      }
      finally
      {
         if (bos != null)
         {
            try
            {
               bos.flush();
               bos.close();
            }
            catch (Exception e)
            {
            }
         }
      }
   }

   private String getPageName()
   {
      return getUserInput("Page name");
   }

   private String getUserInput(String label)
   {
      indent();
      System.out.printf("%s: ", label);
      Scanner scanner = new Scanner(System.in);
      return scanner.next();
   }

   private String getProperty(String propertyName, String defaultValue, Properties properties)
   {
      String value = properties.getProperty(propertyName);
      if (value == null || value.trim().length() == 0) return defaultValue;

      return value;
   }

   private String getProperty(String propertyName, Properties properties) throws Exception
   {
      String value = getProperty(propertyName, null, properties);
      if (value == null) throw new Exception("Configuration property " + propertyName + " is required.");

      return value;
   }

   private void indent()
   {
      for (int i = 0; i < level; i++)
      {
         System.out.print("   ");
      }
   }
}