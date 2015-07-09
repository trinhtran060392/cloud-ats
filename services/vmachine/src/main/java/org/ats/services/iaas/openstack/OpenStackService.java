/**
 * 
 */
package org.ats.services.iaas.openstack;

import java.io.OutputStream;
import java.util.List;

import org.ats.common.PageList;
import org.ats.common.ssh.SSHClient;
import org.ats.services.data.MongoDBService;
import org.ats.services.iaas.CreateVMException;
import org.ats.services.iaas.DestroyTenantException;
import org.ats.services.iaas.DestroyVMException;
import org.ats.services.iaas.IaaSServiceInterface;
import org.ats.services.iaas.InitializeTenantException;
import org.ats.services.iaas.RebuildVMException;
import org.ats.services.iaas.StartVMException;
import org.ats.services.iaas.StopVMException;
import org.ats.services.organization.TenantService;
import org.ats.services.organization.entity.reference.SpaceReference;
import org.ats.services.organization.entity.reference.TenantReference;
import org.ats.services.vmachine.VMachine;
import org.ats.services.vmachine.VMachine.Status;
import org.ats.services.vmachine.VMachineFactory;
import org.ats.services.vmachine.VMachineService;
import org.jclouds.ContextBuilder;
import org.jclouds.logging.slf4j.config.SLF4JLoggingModule;
import org.jclouds.openstack.keystone.v2_0.KeystoneApi;
import org.jclouds.openstack.keystone.v2_0.domain.Role;
import org.jclouds.openstack.keystone.v2_0.domain.Tenant;
import org.jclouds.openstack.keystone.v2_0.domain.User;
import org.jclouds.openstack.keystone.v2_0.extensions.RoleAdminApi;
import org.jclouds.openstack.keystone.v2_0.extensions.TenantAdminApi;
import org.jclouds.openstack.keystone.v2_0.extensions.UserAdminApi;
import org.jclouds.openstack.keystone.v2_0.options.CreateTenantOptions;
import org.jclouds.openstack.keystone.v2_0.options.CreateUserOptions;
import org.jclouds.openstack.neutron.v2.NeutronApi;
import org.jclouds.openstack.neutron.v2.domain.ExternalGatewayInfo;
import org.jclouds.openstack.neutron.v2.domain.FloatingIP;
import org.jclouds.openstack.neutron.v2.domain.FloatingIP.CreateFloatingIP;
import org.jclouds.openstack.neutron.v2.domain.Network;
import org.jclouds.openstack.neutron.v2.domain.Network.CreateNetwork;
import org.jclouds.openstack.neutron.v2.domain.Router;
import org.jclouds.openstack.neutron.v2.domain.Router.CreateRouter;
import org.jclouds.openstack.neutron.v2.domain.Rule.CreateBuilder;
import org.jclouds.openstack.neutron.v2.domain.Rule.CreateRule;
import org.jclouds.openstack.neutron.v2.domain.RuleDirection;
import org.jclouds.openstack.neutron.v2.domain.RuleEthertype;
import org.jclouds.openstack.neutron.v2.domain.RuleProtocol;
import org.jclouds.openstack.neutron.v2.domain.SecurityGroup;
import org.jclouds.openstack.neutron.v2.domain.Subnet;
import org.jclouds.openstack.neutron.v2.domain.Subnet.CreateSubnet;
import org.jclouds.openstack.neutron.v2.extensions.FloatingIPApi;
import org.jclouds.openstack.neutron.v2.extensions.RouterApi;
import org.jclouds.openstack.neutron.v2.extensions.SecurityGroupApi;
import org.jclouds.openstack.neutron.v2.features.NetworkApi;
import org.jclouds.openstack.nova.v2_0.NovaApi;
import org.jclouds.openstack.nova.v2_0.domain.Address;
import org.jclouds.openstack.nova.v2_0.domain.Server;
import org.jclouds.openstack.nova.v2_0.domain.ServerCreated;
import org.jclouds.openstack.nova.v2_0.features.FlavorApi;
import org.jclouds.openstack.nova.v2_0.features.ImageApi;
import org.jclouds.openstack.nova.v2_0.features.ServerApi;
import org.jclouds.openstack.nova.v2_0.options.CreateServerOptions;
import org.jclouds.openstack.v2_0.domain.Resource;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.Session;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;

/**
 * @author <a href="mailto:haithanh0809@gmail.com">Nguyen Thanh Hai</a>
 *
 * Jul 2, 2015
 */
@Singleton
public class OpenStackService implements IaaSServiceInterface {
  
  private String keystoneURL, adminKeystoneEndpoint, keystoneProvider, neutronProvider, novaProvider, externalNetwork, defaultRole;
  
  private String systemImage, uiImage, nonUIImage;
  
  private DBCollection col;
  
  @Inject
  private TenantService tenantService;
  
  @Inject
  private VMachineService vmachineService;
  
  @Inject
  private VMachineFactory vmachineFactory;
  
  @Inject
  OpenStackService(@Named("org.ats.cloud.openstack.keystone.url") String keystoneURL, 
      @Named("org.ats.cloud.openstack.keystone.admin") String adminKeystoneEndpoint,
      @Named("org.ats.cloud.openstack.keystone.provider") String keystoneProvider,
      @Named("org.ats.cloud.openstack.neutron.provider") String neutronProvider,
      @Named("org.ats.cloud.openstack.nova.provider") String novaProvider,
      @Named("org.ats.cloud.openstack.externalnetwork") String externalNetwork,
      @Named("org.ats.cloud.openstack.defaultrole") String defaultRole,
      @Named("org.ats.cloud.openstack.image.systemm") String systemImage,
      @Named("org.ats.cloud.openstack.image.ui") String uiImage,
      @Named("org.ats.cloud.openstack.image.nonui") String nonUIImage,
      MongoDBService mongo) {
    
    this.keystoneURL = keystoneURL;
    this.adminKeystoneEndpoint = adminKeystoneEndpoint;
    this.keystoneProvider = keystoneProvider;
    this.neutronProvider = neutronProvider;
    this.novaProvider = novaProvider;
    this.externalNetwork = externalNetwork;
    this.defaultRole = defaultRole;
    
    this.systemImage = systemImage;
    this.uiImage = uiImage;
    this.nonUIImage = nonUIImage;
    
    this.col = mongo.getDatabase().getCollection("openstack-identity");
  }
  
  KeystoneApi createKeystoneAPI(String tenant) {
    Iterable<Module> modules = ImmutableSet.<Module> of(new SLF4JLoggingModule());
    
    BasicDBObject credential = getCredential(tenant);
    String identity= credential.getString("identity");
    String password = credential.getString("password");
    
    return ContextBuilder
        .newBuilder(keystoneProvider)
        .credentials(identity, password)
        .endpoint(adminKeystoneEndpoint).modules(modules).buildApi(KeystoneApi.class);
  }
  
  NeutronApi createNeutronAPI(String tenant) {
    Iterable<Module> modules = ImmutableSet.<Module> of(new SLF4JLoggingModule());
    
    BasicDBObject credential = getCredential(tenant);
    String identity= credential.getString("identity");
    String password = credential.getString("password");
    
    return ContextBuilder
        .newBuilder(neutronProvider)
        .credentials(identity, password)
        .endpoint(keystoneURL)
        .modules(modules)
        .buildApi(NeutronApi.class);
  }
  
  NovaApi createNovaApi(String tenant) {
    Iterable<Module> modules = ImmutableSet.<Module> of(new SLF4JLoggingModule());
    
    BasicDBObject credential = getCredential(tenant);
    String identity= credential.getString("identity");
    String password = credential.getString("password");
    
    return ContextBuilder
        .newBuilder(novaProvider)
        .credentials(identity, password)
        .endpoint(keystoneURL)
        .modules(modules)
        .buildApi(NovaApi.class);
  }
  
  public void addCredential(String tenant, String username, String password) {
    String identity = tenant + ":" + username;
    BasicDBObject obj = new BasicDBObject("_id", tenant).append("identity", identity).append("password", password);
    this.col.insert(obj);
  }
  
  public void addCredential(String tenant) {
    String identity = tenant + ":" + tenant; 
    String password = tenant;
    BasicDBObject obj = new BasicDBObject("_id", tenant).append("identity", identity).append("password", password);
    this.col.insert(obj);
  }
  
  private BasicDBObject getCredential(String tenant) {
    return (BasicDBObject) this.col.findOne(new BasicDBObject("_id", tenant));
  }
  
  /** 
   * Initialize OpenStack system for new tenant
   * 
   * @param tenantRef
   * @throws InitializeTenantException
   * @throws CreateVMException
   */
  public void initTenant(TenantReference tenantRef) throws InitializeTenantException, CreateVMException {
    //
    org.ats.services.organization.entity.Tenant atsTenant = tenantRef.get();
    
    KeystoneApi keystoneApi = createKeystoneAPI("admin");
    Optional<? extends TenantAdminApi> tenantAdminApiExtension = keystoneApi.getTenantAdminApi();
    
    if (tenantAdminApiExtension.isPresent()) {

      //Create tenant on openstack
      TenantAdminApi tenantAdminApi = tenantAdminApiExtension.get();
      CreateTenantOptions tenantOptions = CreateTenantOptions.Builder.description(tenantRef.getId()).enabled(true);
      Tenant tenant = tenantAdminApi.create(tenantRef.getId(), tenantOptions);
      atsTenant.put("tenant_id", tenant.getId());
      
      
      Optional<? extends UserAdminApi> userAdminApiExtension = keystoneApi.getUserAdminApi();
      if (userAdminApiExtension.isPresent()) {
        UserAdminApi userAdminApi = userAdminApiExtension.get();
        CreateUserOptions userOptions = CreateUserOptions.Builder
            .tenant(tenant.getId()).email("admin@" + tenantRef.getId()).enabled(true);

        //Create user on openstack and add credential to database
        User user = userAdminApi.create(tenantRef.getId(), tenantRef.getId(), userOptions);
        Optional<? extends RoleAdminApi> roleApiExtension = keystoneApi.getRoleAdminApi();
        if (roleApiExtension.isPresent()) {
          RoleAdminApi roleAdminApi = roleApiExtension.get();
          String roleId = null;
          for(Role role : roleAdminApi.list()) {
            if (defaultRole.equals(role.getName())) {
              roleId = role.getId();
              break;
            }
          }
          if (roleId == null) {
            throw new InitializeTenantException("The default role did not exist. Contact system administrator");
          }
          tenantAdminApi.addRoleOnTenant(tenant.getId(), user.getId(), roleId);
          addCredential(tenantRef.getId());
          atsTenant.put("user_id", user.getId());
          
          //Create tenant's network on openstack
          NeutronApi neutronAPI = createNeutronAPI(tenantRef.getId());
          String region = neutronAPI.getConfiguredRegions().iterator().next();
          NetworkApi networkAPI = neutronAPI.getNetworkApi(region); 
          Network network = networkAPI.create(CreateNetwork.createBuilder(tenantRef.getId() + "-network").build());
          atsTenant.put("network_id", network.getId());
          
          CreateSubnet createSubnet = CreateSubnet.createBuilder(network.getId(), "192.168.1.0/24").ipVersion(4).name(tenantRef.getId() + "-subnet").build();
          Subnet subnet = neutronAPI.getSubnetApi(region).create(createSubnet);
          atsTenant.put("subnet_id", subnet.getId());
          
          String externalNetworkId = null;
          for (Network sel : networkAPI.list().concat()) {
            if (externalNetwork.equals(sel.getName())) {
              externalNetworkId = sel.getId();
              break;
            }
          }
          if (externalNetworkId == null) {
            throw new InitializeTenantException("Can not create a tenant without external network. Contact system administrator");
          }
          
          //Add security group
          updateDefaultSecurityGroup(tenantRef);
          
          //Create tenant's router
          Optional<? extends RouterApi> routerApiExtension = neutronAPI.getRouterApi(region);
          if(routerApiExtension.isPresent()){
            RouterApi routerApi = routerApiExtension.get();
            ExternalGatewayInfo externalGateway = ExternalGatewayInfo.builder().networkId(externalNetworkId).build();
            Router router = routerApi.create(CreateRouter.createBuilder()
                .name(tenantRef.getId() + "-router").adminStateUp(true)                                       
                .externalGatewayInfo(externalGateway)
                .build());

            routerApi.addInterfaceForSubnet(router.getId(), subnet.getId());
            atsTenant.put("router_id", router.getId());
            tenantService.update(atsTenant);
            
            //Create system vm for public space
            createSystemVM(tenantRef, null);
          } else{
            throw new InitializeTenantException("Can not create a tenant without router. Contact system administrator");
          }
          
        } else {
          throw new InitializeTenantException("Can not create a tenant without role. Contact system administrator");
        }
        
      } else {
        throw new InitializeTenantException("Can not create a tenant without admin user. Contact system administrator");
      }
      
    } else {
      throw new InitializeTenantException("Can not create a tenant without admin tenant. Contact system administrator");
    }
  }
  
  @Override
  public void destroyTenant(TenantReference tenantRef) throws DestroyTenantException, DestroyVMException {
   org.ats.services.organization.entity.Tenant atsTenant = tenantService.get(tenantRef.getId(), "tenant_id", "user_id", "network_id", "subnet_id", "router_id"); 

   //Destroy router of tenant
   NeutronApi neutronAPI = createNeutronAPI("admin");
   String region = neutronAPI.getConfiguredRegions().iterator().next();
   NetworkApi networkAPI = neutronAPI.getNetworkApi(region);

   Optional<? extends RouterApi> routerApiExtension = neutronAPI.getRouterApi(region);
   if(routerApiExtension.isPresent()){
     RouterApi routerApi = routerApiExtension.get();
     routerApi.removeInterfaceForSubnet(atsTenant.getString("router_id"), atsTenant.getString("subnet_id"));
     
     routerApi.delete(atsTenant.getString("router_id"));
     atsTenant.put("router_id", null);
     atsTenant.put("subnet_id", null);
   }
   
   //Destroy vm of tenant
   PageList<VMachine> page = vmachineService.query(new BasicDBObject("tenant", tenantRef.toJSon()));
   while (page.hasNext()) {
     List<VMachine> list = page.next();
     for (VMachine vm : list) {
       destroy(vm);
     }
   }
   
   //destroy network
   networkAPI.delete(atsTenant.getString("network_id"));
   atsTenant.put("network_id", null);
   
   //Destroy tenant and user
   KeystoneApi keystoneApi = createKeystoneAPI("admin");
   Optional<? extends TenantAdminApi> tenantAdminApiExtension = keystoneApi.getTenantAdminApi();
   
   if (tenantAdminApiExtension.isPresent()) {
     TenantAdminApi tenantAdminApi = tenantAdminApiExtension.get();
     tenantAdminApi.delete(atsTenant.getString("tenant_id"));
     atsTenant.put("tenant_id", null);
     
     Optional<? extends UserAdminApi> userAdminApiExtension = keystoneApi.getUserAdminApi();
     if (userAdminApiExtension.isPresent()) {
       UserAdminApi userAdminApi = userAdminApiExtension.get();
       userAdminApi.delete(atsTenant.getString("user_id"));
       atsTenant.put("user_id", null);
       
       tenantService.update(atsTenant);
       
     } else {
       throw new DestroyTenantException("Can not destroy a tenant without admin user. Contact system administrator");
     }
   } else {
     throw new DestroyTenantException("Can not destroy a tenant without admin tenant. Contact system administrator");
   }
  }

  @Override
  public VMachine start(VMachine machine) throws StartVMException {
    NovaApi novaApi = createNovaApi(machine.getTenant().getId());
    ServerApi serverApi = novaApi.getServerApi(novaApi.getConfiguredRegions().iterator().next());
    serverApi.start(machine.getId());
    Server server = null;
    do {
      server = serverApi.get(machine.getId());
    } while(!server.getStatus().equals(Server.Status.ACTIVE));
    machine.setStatus(VMachine.Status.Started);
    vmachineService.update(machine);
    return machine;
  }

  @Override
  public VMachine stop(VMachine machine) throws StopVMException {
    NovaApi novaApi = createNovaApi(machine.getTenant().getId());
    ServerApi serverApi = novaApi.getServerApi(novaApi.getConfiguredRegions().iterator().next());
    serverApi.stop(machine.getId());
    Server server = null;
    do {
      server = serverApi.get(machine.getId());
    } while(!server.getStatus().equals(Server.Status.SHUTOFF));
    machine.setStatus(VMachine.Status.Stopped);
    vmachineService.update(machine);
    return machine;
  }

  @Override
  public VMachine rebuild(VMachine machine) throws RebuildVMException {
    throw new RebuildVMException("Unsupported operation");
  }

  @Override
  public void destroy(VMachine machine) throws DestroyVMException {
    NovaApi novaApi = createNovaApi(machine.getTenant().getId());
    ServerApi serverApi = novaApi.getServerApi(novaApi.getConfiguredRegions().iterator().next());
    serverApi.delete(machine.getId());
    Server server = null;
    do {
      server = serverApi.get(machine.getId());
    }
    while (server != null);
  }

  @Override
  public VMachine createSystemVM(TenantReference tenant, SpaceReference space) throws CreateVMException {
    String flavorId = getFlavorIdByName(tenant, "m1.small");
    String imageId = getImageIdByName(tenant, systemImage);
    VMachine vm = createVM(imageId, flavorId, true, false, tenant, space);

    //create floating ip for system vm
    FloatingIP floatingIp = getFloatingIPAvailable(tenant);
    NovaApi novaApi = createNovaApi(tenant.getId());
    String region = novaApi.getConfiguredRegions().iterator().next();
    org.jclouds.openstack.nova.v2_0.extensions.FloatingIPApi floatingIpApi = novaApi.getFloatingIPApi(region).get();
    floatingIpApi.addToServer(floatingIp.getFloatingIpAddress(), vm.getId());
    vm.setPublicIp(floatingIp.getFloatingIpAddress());
    vmachineService.update(vm);
    
    //restart jenkins
    try {
      if (SSHClient.checkEstablished(floatingIp.getFloatingIpAddress(), 22, 300)) {
        Session session = SSHClient.getSession(floatingIp.getFloatingIpAddress(), 22, "cloudats", "#CloudATS");
        ChannelExec channel = (ChannelExec) session.openChannel("exec");
        String command = "nohup java -jar jenkins.war --prefix=/jenkins > log.txt 2 > /dev/null &";                
        channel.setCommand(command);
        OutputStream out = channel.getOutputStream();
        channel.connect();

        out.write(("#CloudATS\n").getBytes());
        out.flush();
        SSHClient.printOut(System.out, channel);
        channel.disconnect();
        session.disconnect();
      }
    } catch (Exception e) {
      CreateVMException ex = new CreateVMException("Cannot connect to vm");
      ex.setStackTrace(e.getStackTrace());
      throw ex;
    }
    
    return vm;
  }
  
  private FloatingIP getFloatingIPAvailable(TenantReference tenant) {
    NeutronApi neutronApi = createNeutronAPI(tenant.getId());
    NovaApi novaApi = createNovaApi(tenant.getId());
    
    String region = novaApi.getConfiguredRegions().iterator().next();
    
    FloatingIPApi floatingIpApi = neutronApi.getFloatingIPApi(region).get();
    
    for (FloatingIP sel : floatingIpApi.list().concat()) {
      if (sel.getFixedIpAddress() == null) {
        return sel;
      }
    }
    
    NetworkApi networkApi = neutronApi.getNetworkApi(region);

    String externalNetworkId = null;
    for (Network sel : networkApi.list().concat()) {
      if (externalNetwork.equals(sel.getName())) {
        externalNetworkId = sel.getId();
        break;
      }
    }
    
    return floatingIpApi.create(CreateFloatingIP.createBuilder(externalNetworkId).build());
  }

  @Override
  public VMachine createTestVM(TenantReference tenant, SpaceReference space, boolean hasUI) throws CreateVMException {
    String flavorId = getFlavorIdByName(tenant, "m1.small");
    String imageId = hasUI ? getImageIdByName(tenant, uiImage) : getImageIdByName(tenant, nonUIImage); 
    return createVM(imageId, flavorId, false, hasUI, tenant, space);
  }

  private String getFlavorIdByName(TenantReference tenantRef, String flavorName) {
    NovaApi novaApi = createNovaApi(tenantRef.getId());
    FlavorApi flavorApi = novaApi.getFlavorApi(novaApi.getConfiguredRegions().iterator().next());
    for (Resource resource : flavorApi.list().concat()) {
      if (flavorName.equals(resource.getName())) return resource.getId();
    }
    return null;
  }
  
  private String getImageIdByName(TenantReference tenantRef, String imageName) {
    NovaApi novaApi = createNovaApi(tenantRef.getId());
    ImageApi imageApi = novaApi.getImageApi(novaApi.getConfiguredRegions().iterator().next());
    for (Resource resource : imageApi.list().concat()) {
      if (imageName.equals(resource.getName())) return resource.getId();
    }
    return null;
  }
  
  private VMachine createVM(String imageId, String flavorId, boolean system, boolean hasUI, TenantReference tenant, SpaceReference space) {
    NovaApi novaApi = createNovaApi(tenant.getId());
    ServerApi serverApi = novaApi.getServerApi(novaApi.getConfiguredRegions().iterator().next());
    StringBuilder sb = new StringBuilder(tenant.getId()).append(system ? "-system" : "").append(hasUI ? "-ui-" : "-nonui-");
    sb.append(space == null ? "public" : space.getId());
    String serverName = sb.toString();
    sb.setLength(0);

    sb.append("#cloud-config").append("\n");
    if (hasUI) {
      sb.append("hostname: ").append(serverName).append("\n");
      sb.append("fqdn: ").append(serverName).append(".cloud-ats.org").append("\n");
      sb.append("manage_etc_hosts: true").append("\n");
    }
    
//    if (system) {
//      sb.append("runcmd:").append("\n");
//      sb.append("- [ service, jenkins, restart ]");
//    }
    
    ServerCreated serverCreated = serverApi.create(serverName, imageId, flavorId, new CreateServerOptions().userData(sb.toString().getBytes()));
    Server server = null;
    do {
      server = serverApi.get(serverCreated.getId());
    }
    while (!server.getStatus().equals(Server.Status.ACTIVE));
    
    Address address = server.getAddresses().entries().iterator().next().getValue();
    VMachine vm = vmachineFactory.create(serverCreated.getId(), tenant, space, system, hasUI, null, address.getAddr(), Status.Started);
    vmachineService.create(vm);
    return vm;
  }
  
  private void updateDefaultSecurityGroup(TenantReference tenantRef) {
    NovaApi novaApi = createNovaApi(tenantRef.getId());
    NeutronApi neutronApi = createNeutronAPI(tenantRef.getId());
    Optional<SecurityGroupApi> optional = neutronApi.getSecurityGroupApi(novaApi.getConfiguredRegions().iterator().next());
    if (optional.isPresent()) {
      SecurityGroupApi securityGroupApi = optional.get();
      SecurityGroup defaultGroup = securityGroupApi.listSecurityGroups().concat().get(0);

      //create icmp 
      CreateBuilder createBuilder = CreateRule.createBuilder(RuleDirection.INGRESS, defaultGroup.getId());
      createBuilder.ethertype(RuleEthertype.IPV4);
      createBuilder.protocol(RuleProtocol.ICMP);
      createBuilder.portRangeMin(0);
      createBuilder.portRangeMax(255);
      securityGroupApi.create(createBuilder.build());

      //create port 22
      createBuilder = CreateRule.createBuilder(RuleDirection.INGRESS, defaultGroup.getId());
      createBuilder.ethertype(RuleEthertype.IPV4);
      createBuilder.protocol(RuleProtocol.TCP);
      createBuilder.portRangeMin(22);
      createBuilder.portRangeMax(22);
      securityGroupApi.create(createBuilder.build());

      //create port 80
      createBuilder = CreateRule.createBuilder(RuleDirection.INGRESS, defaultGroup.getId());
      createBuilder.ethertype(RuleEthertype.IPV4);
      createBuilder.protocol(RuleProtocol.TCP);
      createBuilder.portRangeMin(80);
      createBuilder.portRangeMax(80);
      securityGroupApi.create(createBuilder.build());

      //create port 443
      createBuilder = CreateRule.createBuilder(RuleDirection.INGRESS, defaultGroup.getId());
      createBuilder.ethertype(RuleEthertype.IPV4);
      createBuilder.protocol(RuleProtocol.TCP);
      createBuilder.portRangeMin(443);
      createBuilder.portRangeMax(443);
      securityGroupApi.create(createBuilder.build());

      //create port 1099
      createBuilder = CreateRule.createBuilder(RuleDirection.INGRESS, defaultGroup.getId());
      createBuilder.ethertype(RuleEthertype.IPV4);
      createBuilder.protocol(RuleProtocol.TCP);
      createBuilder.portRangeMin(1099);
      createBuilder.portRangeMax(1099);
      securityGroupApi.create(createBuilder.build());

      //create port 5900
      createBuilder = CreateRule.createBuilder(RuleDirection.INGRESS, defaultGroup.getId());
      createBuilder.ethertype(RuleEthertype.IPV4);
      createBuilder.protocol(RuleProtocol.TCP);
      createBuilder.portRangeMin(5900);
      createBuilder.portRangeMax(5900);
      securityGroupApi.create(createBuilder.build());

      //create port 8080
      createBuilder = CreateRule.createBuilder(RuleDirection.INGRESS, defaultGroup.getId());
      createBuilder.ethertype(RuleEthertype.IPV4);
      createBuilder.protocol(RuleProtocol.TCP);
      createBuilder.portRangeMin(8080);
      createBuilder.portRangeMax(8080);
      securityGroupApi.create(createBuilder.build());

      //create port 8081
      createBuilder = CreateRule.createBuilder(RuleDirection.INGRESS, defaultGroup.getId());
      createBuilder.ethertype(RuleEthertype.IPV4);
      createBuilder.protocol(RuleProtocol.TCP);
      createBuilder.portRangeMin(8081);
      createBuilder.portRangeMax(8081);
      securityGroupApi.create(createBuilder.build());
    }
  }
}
