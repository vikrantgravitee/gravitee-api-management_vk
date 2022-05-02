/**
 * Copyright (C) 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.rest.api.service;

import static io.gravitee.rest.api.model.WorkflowReferenceType.API;
import static io.gravitee.rest.api.model.WorkflowType.REVIEW;
import static io.gravitee.rest.api.model.api.ApiLifecycleState.*;
import static java.util.Arrays.asList;
import static java.util.Collections.*;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.internal.util.collections.Sets.newSet;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ser.PropertyFilter;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;
import io.gravitee.definition.jackson.datatype.GraviteeMapper;
import io.gravitee.definition.model.*;
import io.gravitee.definition.model.endpoint.HttpEndpoint;
import io.gravitee.definition.model.services.Services;
import io.gravitee.definition.model.services.healthcheck.HealthCheckService;
import io.gravitee.repository.exceptions.TechnicalException;
import io.gravitee.repository.management.api.ApiRepository;
import io.gravitee.repository.management.model.Api;
import io.gravitee.repository.management.model.ApiLifecycleState;
import io.gravitee.repository.management.model.Workflow;
import io.gravitee.rest.api.model.*;
import io.gravitee.rest.api.model.api.ApiEntity;
import io.gravitee.rest.api.model.api.UpdateApiEntity;
import io.gravitee.rest.api.model.parameters.Key;
import io.gravitee.rest.api.model.parameters.ParameterReferenceType;
import io.gravitee.rest.api.model.permissions.ApiPermission;
import io.gravitee.rest.api.model.permissions.RolePermissionAction;
import io.gravitee.rest.api.model.permissions.RoleScope;
import io.gravitee.rest.api.model.permissions.SystemRole;
import io.gravitee.rest.api.service.builder.EmailNotificationBuilder;
import io.gravitee.rest.api.service.common.GraviteeContext;
import io.gravitee.rest.api.service.exceptions.*;
import io.gravitee.rest.api.service.impl.ApiServiceImpl;
import io.gravitee.rest.api.service.jackson.filter.ApiPermissionFilter;
import io.gravitee.rest.api.service.notification.NotificationTemplateService;
import io.gravitee.rest.api.service.search.SearchEngineService;
import java.io.Reader;
import java.util.*;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.internal.util.collections.Sets;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * @author Azize ELAMRANI (azize.elamrani at graviteesource.com)
 * @author Nicolas GERAUD (nicolas.geraud at graviteesource.com)
 */
@RunWith(MockitoJUnitRunner.class)
public class ApiService_UpdateTest {

    private static final String API_ID = "id-api";
    private static final String DEFAULT_ENVIRONMENT_ID = "DEFAULT";
    private static final String API_NAME = "myAPI";
    private static final String USER_NAME = "myUser";
    public static final String API_DEFINITION =
        "{\n" +
        "  \"description\" : \"Gravitee.io\",\n" +
        "  \"paths\" : { },\n" +
        "  \"path_mappings\":[],\n" +
        "  \"proxy\": {\n" +
        "    \"virtual_hosts\": [{\n" +
        "      \"path\": \"/test\"\n" +
        "    }],\n" +
        "    \"strip_context_path\": false,\n" +
        "    \"preserve_host\":false,\n" +
        "    \"logging\": {\n" +
        "      \"mode\":\"CLIENT_PROXY\",\n" +
        "      \"condition\":\"condition\"\n" +
        "    },\n" +
        "    \"groups\": [\n" +
        "      {\n" +
        "        \"name\": \"default-group\",\n" +
        "        \"endpoints\": [\n" +
        "          {\n" +
        "            \"name\": \"default\",\n" +
        "            \"target\": \"http://test\",\n" +
        "            \"weight\": 1,\n" +
        "            \"backup\": false,\n" +
        "            \"type\": \"HTTP\",\n" +
        "            \"http\": {\n" +
        "              \"connectTimeout\": 5000,\n" +
        "              \"idleTimeout\": 60000,\n" +
        "              \"keepAlive\": true,\n" +
        "              \"readTimeout\": 10000,\n" +
        "              \"pipelining\": false,\n" +
        "              \"maxConcurrentConnections\": 100,\n" +
        "              \"useCompression\": true,\n" +
        "              \"followRedirects\": false,\n" +
        "              \"encodeURI\":false\n" +
        "            }\n" +
        "          }\n" +
        "        ],\n" +
        "        \"load_balancing\": {\n" +
        "          \"type\": \"ROUND_ROBIN\"\n" +
        "        },\n" +
        "        \"http\": {\n" +
        "          \"connectTimeout\": 5000,\n" +
        "          \"idleTimeout\": 60000,\n" +
        "          \"keepAlive\": true,\n" +
        "          \"readTimeout\": 10000,\n" +
        "          \"pipelining\": false,\n" +
        "          \"maxConcurrentConnections\": 100,\n" +
        "          \"useCompression\": true,\n" +
        "          \"followRedirects\": false,\n" +
        "          \"encodeURI\":false\n" +
        "        }\n" +
        "      }\n" +
        "    ]\n" +
        "  }\n" +
        "}\n";

    @InjectMocks
    private ApiServiceImpl apiService = new ApiServiceImpl();

    @Mock
    private ApiRepository apiRepository;

    @Mock
    private MembershipService membershipService;

    @Mock
    private RoleService roleService;

    @Spy
    private ObjectMapper objectMapper = new GraviteeMapper();

    private final UpdateApiEntity updateApiEntity = new UpdateApiEntity();

    private final Api api = new Api();
    private final Api updatedApi = new Api();

    @Mock
    private UserService userService;

    @Mock
    private AuditService auditService;

    @Mock
    private SearchEngineService searchEngineService;

    @Mock
    private TagService tagService;

    @Mock
    private ParameterService parameterService;

    @Mock
    private WorkflowService workflowService;

    @Mock
    private VirtualHostService virtualHostService;

    @Mock
    private CategoryService categoryService;

    @Mock
    private NotifierService notifierService;

    @Mock
    private PolicyService policyService;

    @Mock
    private GroupService groupService;

    @Mock
    private ApiMetadataService apiMetadataService;

    @Mock
    private EmailService emailService;

    @Mock
    private NotificationTemplateService notificationTemplateService;

    @Before
    public void setUp() {
        PropertyFilter apiMembershipTypeFilter = new ApiPermissionFilter();
        objectMapper.setFilterProvider(
            new SimpleFilterProvider(Collections.singletonMap("apiMembershipTypeFilter", apiMembershipTypeFilter))
        );
        GraviteeContext.setCurrentEnvironment(DEFAULT_ENVIRONMENT_ID);

        final SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(mock(Authentication.class));
        SecurityContextHolder.setContext(securityContext);

        api.setId(API_ID);
        api.setDefinition("{\"id\": \"" + API_ID + "\",\"name\": \"" + API_NAME + "\",\"proxy\": {\"context_path\": \"/old\"}}");
        api.setEnvironmentId(DEFAULT_ENVIRONMENT_ID);

        updateApiEntity.setVersion("v1");
        updateApiEntity.setName(API_NAME);
        updateApiEntity.setDescription("Ma description");

        updatedApi.setId(API_ID);
        updatedApi.setName(API_NAME);
        updatedApi.setEnvironmentId(DEFAULT_ENVIRONMENT_ID);

        when(notificationTemplateService.resolveInlineTemplateWithParam(anyString(), any(Reader.class), any()))
            .thenReturn("toDecode=decoded-value");
        MembershipEntity primaryOwner = new MembershipEntity();
        primaryOwner.setMemberType(MembershipMemberType.USER);
        when(membershipService.getPrimaryOwner(eq(GraviteeContext.getCurrentOrganization()), eq(MembershipReferenceType.API), any()))
            .thenReturn(primaryOwner);
        reset(searchEngineService);
    }

    @After
    public void tearDown() {
        GraviteeContext.cleanContext();
    }

    @AfterClass
    public static void cleanSecurityContextHolder() {
        // reset authentication to avoid side effect during test executions.
        SecurityContextHolder.setContext(
            new SecurityContext() {
                @Override
                public Authentication getAuthentication() {
                    return null;
                }

                @Override
                public void setAuthentication(Authentication authentication) {}
            }
        );
    }

    @Test
    public void shouldUpdate() throws TechnicalException {
        prepareUpdate();

        final ApiEntity apiEntity = apiService.update(API_ID, updateApiEntity);

        assertNotNull(apiEntity);
        assertEquals(API_NAME, apiEntity.getName());
        verify(searchEngineService, times(1)).index(any(), eq(false));
    }

    @Test(expected = ApiNotFoundException.class)
    public void shouldNotUpdateBecauseNotFound() throws TechnicalException {
        when(apiRepository.findById(API_ID)).thenReturn(Optional.empty());

        apiService.update(API_ID, updateApiEntity);
    }

    @Test(expected = TechnicalManagementException.class)
    public void shouldNotUpdateBecauseTechnicalException() throws TechnicalException {
        updateApiEntity.setVersion("v1");
        updateApiEntity.setName(API_NAME);
        updateApiEntity.setDescription("Ma description");
        final Proxy proxy = new Proxy();
        EndpointGroup endpointGroup = new EndpointGroup();
        endpointGroup.setName("endpointGroupName");
        Endpoint endpoint = new HttpEndpoint("endpointName", null);
        endpointGroup.setEndpoints(singleton(endpoint));
        proxy.setGroups(singleton(endpointGroup));
        updateApiEntity.setProxy(proxy);
        proxy.setVirtualHosts(Collections.singletonList(new VirtualHost("/context")));
        updateApiEntity.setLifecycleState(CREATED);

        when(apiRepository.findById(API_ID)).thenReturn(Optional.of(api));
        api.setApiLifecycleState(ApiLifecycleState.CREATED);
        when(apiRepository.update(any())).thenThrow(TechnicalException.class);

        apiService.update(API_ID, updateApiEntity);
    }

    @Test(expected = EndpointNameInvalidException.class)
    public void shouldNotUpdateWithInvalidEndpointGroupName() throws TechnicalException {
        when(apiRepository.findById(API_ID)).thenReturn(Optional.of(api));

        final Proxy proxy = new Proxy();
        updateApiEntity.setProxy(proxy);
        proxy.setVirtualHosts(Collections.singletonList(new VirtualHost("/new")));
        final EndpointGroup group = new EndpointGroup();
        group.setName("inva:lid");
        proxy.setGroups(singleton(group));
        group.setEndpoints(singleton(mock(Endpoint.class)));
        apiService.update(API_ID, updateApiEntity);

        fail("should throw EndpointNameInvalidException");
    }

    @Test(expected = EndpointNameInvalidException.class)
    public void shouldNotUpdateWithInvalidEndpointName() throws TechnicalException {
        when(apiRepository.findById(API_ID)).thenReturn(Optional.of(api));

        Endpoint endpoint = mock(Endpoint.class);
        when(endpoint.getName()).thenReturn("inva:lid");

        final EndpointGroup group = new EndpointGroup();
        group.setName("group");
        group.setEndpoints(singleton(endpoint));

        final Proxy proxy = new Proxy();
        proxy.setVirtualHosts(Collections.singletonList(new VirtualHost("/new")));
        proxy.setGroups(singleton(group));
        updateApiEntity.setProxy(proxy);

        apiService.update(API_ID, updateApiEntity);

        fail("should throw EndpointNameInvalidException");
    }

    @Test(expected = InvalidDataException.class)
    public void shouldNotUpdateWithInvalidPolicyConfiguration() throws TechnicalException {
        prepareUpdate();

        HashMap<String, List<Rule>> paths = new HashMap<>();
        ArrayList<Rule> rules = new ArrayList<>();
        Rule rule = new Rule();
        Policy policy = new Policy();
        rule.setPolicy(policy);
        rule.setEnabled(true);
        rules.add(rule);
        paths.put("/", rules);

        updateApiEntity.setPaths(paths);
        doThrow(new InvalidDataException()).when(policyService).validatePolicyConfiguration(any(Policy.class));

        apiService.update(API_ID, updateApiEntity);

        fail("should throw InvalidDataException");
    }

    @Test(expected = InvalidDataException.class)
    public void shouldNotUpdateWithPOGroups() throws TechnicalException {
        prepareUpdate();

        Set<String> groups = Sets.newSet("group-with-po");
        updateApiEntity.setGroups(groups);

        GroupEntity poGroup = new GroupEntity();
        poGroup.setId("group-with-po");
        poGroup.setApiPrimaryOwner("a-api-po-user");
        Set<GroupEntity> groupEntitySet = Sets.newSet(poGroup);
        //        when(groupService.findByIds(groups)).thenReturn(groupEntitySet);
        when(groupService.findByIds(groups)).thenThrow(GroupsNotFoundException.class);

        apiService.update(API_ID, updateApiEntity);

        fail("should throw InvalidDataException");
    }

    private void prepareUpdate() throws TechnicalException {
        when(apiRepository.findById(API_ID)).thenReturn(Optional.of(api));
        when(apiRepository.update(any())).thenReturn(updatedApi);

        api.setName(API_NAME);
        api.setApiLifecycleState(ApiLifecycleState.CREATED);

        updateApiEntity.setName(API_NAME);
        updateApiEntity.setVersion("v1");
        updateApiEntity.setDescription("Ma description");

        final Proxy proxy = new Proxy();
        EndpointGroup endpointGroup = new EndpointGroup();
        endpointGroup.setName("endpointGroupName");
        Endpoint endpoint = new HttpEndpoint("endpointName", null);
        endpointGroup.setEndpoints(singleton(endpoint));
        proxy.setGroups(singleton(endpointGroup));
        Cors cors = new Cors();
        cors.setEnabled(false);
        proxy.setCors(cors);
        updateApiEntity.setProxy(proxy);
        updateApiEntity.setLifecycleState(CREATED);
        proxy.setVirtualHosts(Collections.singletonList(new VirtualHost("/context")));

        RoleEntity poRoleEntity = new RoleEntity();
        poRoleEntity.setName(SystemRole.PRIMARY_OWNER.name());
        poRoleEntity.setScope(RoleScope.API);

        MemberEntity po = new MemberEntity();
        po.setId(USER_NAME);
        po.setReferenceId(API_ID);
        po.setReferenceType(MembershipReferenceType.API);
        po.setRoles(Collections.singletonList(poRoleEntity));
        when(membershipService.getMembersByReferencesAndRole(any(), any(), any())).thenReturn(Collections.singleton(po));
        when(roleService.findPrimaryOwnerRoleByOrganization(any(), any())).thenReturn(poRoleEntity);
    }

    @Test
    public void shouldUpdateWithAllowedTag() throws TechnicalException {
        prepareUpdate();
        updateApiEntity.setTags(singleton("public"));
        api.setDefinition(
            "{\"id\": \"" + API_ID + "\",\"name\": \"" + API_NAME + "\",\"proxy\": {\"context_path\": \"/old\"} ,\"tags\": [\"public\"]}"
        );
        final ApiEntity apiEntity = apiService.update(API_ID, updateApiEntity);
        assertNotNull(apiEntity);
        assertEquals(API_NAME, apiEntity.getName());
        verify(searchEngineService, times(1)).index(any(), eq(false));
    }

    @Test
    public void shouldNotDuplicateLabels() throws TechnicalException {
        prepareUpdate();
        updateApiEntity.setLabels(asList("label1", "label1"));
        api.setDefinition(
            "{\"id\": \"" + API_ID + "\",\"name\": \"" + API_NAME + "\",\"proxy\": {\"context_path\": \"/old\"} ,\"labels\": [\"public\"]}"
        );
        final ApiEntity apiEntity = apiService.update(API_ID, updateApiEntity);
        verify(apiRepository).update(argThat(api -> api.getLabels().size() == 1));
        assertNotNull(apiEntity);
        verify(searchEngineService, times(1)).index(any(), eq(false));
    }

    @Test
    public void shouldUpdateWithExistingAllowedTag() throws TechnicalException {
        prepareUpdate();

        updateApiEntity.setTags(singleton("private"));
        Proxy proxy = new Proxy();
        EndpointGroup endpointGroup = new EndpointGroup();
        endpointGroup.setName("endpointGroupName");
        Endpoint endpoint = new HttpEndpoint("EndpointName", null);
        endpointGroup.setEndpoints(singleton(endpoint));
        proxy.setGroups(singleton(endpointGroup));
        updateApiEntity.setProxy(proxy);
        api.setDefinition(
            "{\"id\": \"" + API_ID + "\",\"name\": \"" + API_NAME + "\",\"proxy\": {\"context_path\": \"/old\"} ,\"tags\": [\"public\"]}"
        );
        when(tagService.findByUser(any(), any(), any())).thenReturn(Sets.newSet("public", "private"));
        final ApiEntity apiEntity = apiService.update(API_ID, updateApiEntity);
        assertNotNull(apiEntity);
        assertEquals(API_NAME, apiEntity.getName());
        verify(searchEngineService, times(1)).index(any(), eq(false));
    }

    @Test
    public void shouldUpdateWithExistingAllowedTags() throws TechnicalException {
        prepareUpdate();
        updateApiEntity.setTags(newSet("public", "private"));
        Proxy proxy = new Proxy();
        EndpointGroup endpointGroup = new EndpointGroup();
        endpointGroup.setName("endpointGroupName");
        Endpoint endpoint = new HttpEndpoint("endpointName", null);
        endpointGroup.setEndpoints(singleton(endpoint));
        proxy.setGroups(singleton(endpointGroup));
        updateApiEntity.setProxy(proxy);
        api.setDefinition(
            "{\"id\": \"" + API_ID + "\",\"name\": \"" + API_NAME + "\",\"proxy\": {\"context_path\": \"/old\"} ,\"tags\": [\"public\"]}"
        );
        when(tagService.findByUser(any(), any(), any())).thenReturn(Sets.newSet("public", "private"));
        final ApiEntity apiEntity = apiService.update(API_ID, updateApiEntity);
        assertNotNull(apiEntity);
        assertEquals(API_NAME, apiEntity.getName());
        verify(searchEngineService, times(1)).index(any(), eq(false));
    }

    @Test
    public void shouldUpdateWithExistingNotAllowedTag() throws TechnicalException {
        prepareUpdate();
        updateApiEntity.setTags(newSet("public", "private"));
        api.setDefinition(
            "{\"id\": \"" +
            API_ID +
            "\",\"name\": \"" +
            API_NAME +
            "\",\"proxy\": {\"context_path\": \"/old\"} ,\"tags\": [\"public\", \"private\"]}"
        );
        final ApiEntity apiEntity = apiService.update(API_ID, updateApiEntity);
        assertNotNull(apiEntity);
        assertEquals(API_NAME, apiEntity.getName());
        verify(searchEngineService, times(1)).index(any(), eq(false));
    }

    @Test(expected = InvalidDataException.class)
    public void shouldNotUpdate_NewPlanNotAllowed() throws TechnicalException {
        prepareUpdate();
        Plan plan = new Plan();
        plan.setName("Plan Malicious");
        plan.setStatus(PlanStatus.PUBLISHED.name());
        updateApiEntity.setPlans(List.of(plan));

        api.setDefinition("{\"id\": \"" + API_ID + "\",\"name\": \"" + API_NAME + "\",\"proxy\": {\"context_path\": \"/old\"}}");
        apiService.update(API_ID, updateApiEntity, true);
    }

    @Test(expected = InvalidDataException.class)
    public void shouldNotUpdate_PlanClosed() throws TechnicalException {
        prepareUpdate();
        Plan plan = new Plan();
        plan.setId("MALICIOUS");
        plan.setName("Plan Malicious");
        plan.setStatus(PlanStatus.PUBLISHED.name());
        updateApiEntity.setPlans(List.of(plan));
        api.setDefinition(
            "{\"id\": \"" +
            API_ID +
            "\", \"gravitee\": \"2.0.0\", \"name\": \"" +
            API_NAME +
            "\",\"proxy\": {\"context_path\": \"/old\"}, \"plans\": [{ \"id\": \"MALICIOUS\", \"status\":\"CLOSED\"}]}"
        );
        apiService.update(API_ID, updateApiEntity, true);
    }

    @Test
    public void shouldUpdate_PlanStatusNotChanged() throws TechnicalException {
        prepareUpdate();
        Plan plan = new Plan();
        plan.setId("MALICIOUS");
        plan.setName("Plan Malicious");
        plan.setStatus(PlanStatus.CLOSED.name());
        updateApiEntity.setPlans(List.of(plan));
        api.setDefinition(
            "{\"id\": \"" +
            API_ID +
            "\", \"gravitee\": \"2.0.0\", \"name\": \"" +
            API_NAME +
            "\",\"proxy\": {\"context_path\": \"/old\"}, \"plans\": [{ \"id\": \"MALICIOUS\", \"status\":\"CLOSED\"}]}"
        );
        apiService.update(API_ID, updateApiEntity, true);
    }

    @Test
    public void shouldUpdate_PlanStatusChanged_authorized() throws TechnicalException {
        prepareUpdate();
        Plan plan = new Plan();
        plan.setId("VALID");
        plan.setName("Plan VALID");
        plan.setStatus(PlanStatus.CLOSED.name());
        updateApiEntity.setPlans(List.of(plan));
        api.setDefinition(
            "{\"id\": \"" +
            API_ID +
            "\", \"gravitee\": \"2.0.0\", \"name\": \"" +
            API_NAME +
            "\",\"proxy\": {\"context_path\": \"/old\"}, \"plans\": [{ \"id\": \"VALID\", \"status\":\"PUBLISHED\"}]}"
        );
        apiService.update(API_ID, updateApiEntity, true);
    }

    @Test(expected = TagNotAllowedException.class)
    public void shouldNotUpdateWithNotAllowedTag() throws TechnicalException {
        when(apiRepository.findById(API_ID)).thenReturn(Optional.of(api));
        api.setDefinition(
            "{\"id\": \"" + API_ID + "\",\"name\": \"" + API_NAME + "\",\"proxy\": {\"context_path\": \"/old\"} ,\"tags\": [\"public\"]}"
        );
        updateApiEntity.setTags(singleton("private"));
        final Proxy proxy = new Proxy();
        EndpointGroup endpointGroup = new EndpointGroup();
        Endpoint endpoint = new HttpEndpoint("default", null);
        endpointGroup.setEndpoints(singleton(endpoint));
        proxy.setGroups(singleton(endpointGroup));
        updateApiEntity.setProxy(proxy);
        proxy.setVirtualHosts(Collections.singletonList(new VirtualHost("/context")));
        when(tagService.findByUser(any(), any(), any())).thenReturn(emptySet());
        apiService.update(API_ID, updateApiEntity);
    }

    @Test(expected = TagNotAllowedException.class)
    public void shouldNotUpdateWithExistingNotAllowedTag() throws TechnicalException {
        when(apiRepository.findById(API_ID)).thenReturn(Optional.of(api));
        api.setDefinition(
            "{\"id\": \"" + API_ID + "\",\"name\": \"" + API_NAME + "\",\"proxy\": {\"context_path\": \"/old\"} ,\"tags\": [\"public\"]}"
        );
        updateApiEntity.setTags(singleton("private"));
        final Proxy proxy = new Proxy();
        EndpointGroup endpointGroup = new EndpointGroup();
        Endpoint endpoint = new HttpEndpoint("default", null);
        endpointGroup.setEndpoints(singleton(endpoint));
        proxy.setGroups(singleton(endpointGroup));
        updateApiEntity.setProxy(proxy);
        proxy.setVirtualHosts(Collections.singletonList(new VirtualHost("/context")));
        when(tagService.findByUser(any(), any(), any())).thenReturn(singleton("public"));
        apiService.update(API_ID, updateApiEntity);
    }

    @Test(expected = TagNotAllowedException.class)
    public void shouldNotUpdateWithExistingNotAllowedTags() throws TechnicalException {
        when(apiRepository.findById(API_ID)).thenReturn(Optional.of(api));
        api.setDefinition(
            "{\"id\": \"" +
            API_ID +
            "\",\"name\": \"" +
            API_NAME +
            "\",\"proxy\": {\"context_path\": \"/old\"} ,\"tags\": [\"public\", \"private\"]}"
        );
        updateApiEntity.setTags(emptySet());

        final Proxy proxy = new Proxy();
        EndpointGroup endpointGroup = new EndpointGroup();
        Endpoint endpoint = new HttpEndpoint("default", null);
        endpointGroup.setEndpoints(singleton(endpoint));
        proxy.setGroups(singleton(endpointGroup));
        proxy.setVirtualHosts(Collections.singletonList(new VirtualHost("/context")));

        updateApiEntity.setProxy(proxy);
        when(tagService.findByUser(any(), any(), any())).thenReturn(singleton("private"));
        apiService.update(API_ID, updateApiEntity);
    }

    @Test(expected = InvalidDataException.class)
    public void shouldNotUpdateWithInvalidSchedule() throws TechnicalException {
        prepareUpdate();
        Services services = new Services();
        HealthCheckService healthCheckService = mock(HealthCheckService.class);
        when(healthCheckService.getSchedule()).thenReturn("**");
        services.put(HealthCheckService.class, healthCheckService);
        updateApiEntity.setServices(services);
        apiService.update(API_ID, updateApiEntity);
    }

    @Test
    public void shouldUpdateWithValidSchedule() throws TechnicalException {
        prepareUpdate();
        Services services = new Services();
        HealthCheckService healthCheckService = new HealthCheckService();
        healthCheckService.setSchedule("1,2 */100 5-8 * * *");
        services.put(HealthCheckService.class, healthCheckService);
        updateApiEntity.setServices(services);
        final ApiEntity apiEntity = apiService.update(API_ID, updateApiEntity);

        assertNotNull(apiEntity);
        assertEquals(API_NAME, apiEntity.getName());
        verify(searchEngineService, times(1)).index(any(), eq(false));
    }

    @Test
    public void shouldPublishApi() throws TechnicalException {
        prepareUpdate();
        // from UNPUBLISHED state
        api.setApiLifecycleState(ApiLifecycleState.UNPUBLISHED);
        updateApiEntity.setLifecycleState(PUBLISHED);
        apiService.update(API_ID, updateApiEntity);

        verify(apiRepository).update(argThat(api -> api.getApiLifecycleState().equals(ApiLifecycleState.PUBLISHED)));

        clearInvocations(apiRepository);

        // from CREATED state
        api.setApiLifecycleState(ApiLifecycleState.CREATED);
        updateApiEntity.setLifecycleState(PUBLISHED);
        apiService.update(API_ID, updateApiEntity);

        verify(apiRepository).update(argThat(api -> api.getApiLifecycleState().equals(ApiLifecycleState.PUBLISHED)));
    }

    @Test
    public void shouldUnpublishApi() throws TechnicalException {
        prepareUpdate();
        api.setApiLifecycleState(ApiLifecycleState.PUBLISHED);
        updateApiEntity.setLifecycleState(io.gravitee.rest.api.model.api.ApiLifecycleState.UNPUBLISHED);
        apiService.update(API_ID, updateApiEntity);

        verify(apiRepository).update(argThat(api -> api.getApiLifecycleState().equals(ApiLifecycleState.UNPUBLISHED)));
        verify(searchEngineService, times(1)).index(any(), eq(false));
    }

    @Test
    public void shouldNotChangeLifecycleStateFromUnpublishedToCreated() throws TechnicalException {
        prepareUpdate();
        assertUpdate(ApiLifecycleState.UNPUBLISHED, CREATED, true);
        assertUpdate(ApiLifecycleState.UNPUBLISHED, PUBLISHED, false);
        assertUpdate(ApiLifecycleState.UNPUBLISHED, UNPUBLISHED, false);
        assertUpdate(ApiLifecycleState.UNPUBLISHED, ARCHIVED, false);
    }

    @Test
    public void shouldNotUpdateADeprecatedApi() throws TechnicalException {
        prepareUpdate();
        assertUpdate(ApiLifecycleState.DEPRECATED, CREATED, true);
        assertUpdate(ApiLifecycleState.DEPRECATED, PUBLISHED, true);
        assertUpdate(ApiLifecycleState.DEPRECATED, UNPUBLISHED, true);
        assertUpdate(ApiLifecycleState.DEPRECATED, ARCHIVED, true);
        assertUpdate(ApiLifecycleState.DEPRECATED, DEPRECATED, true);
    }

    @Test
    public void shouldNotChangeLifecycleStateFromArchived() throws TechnicalException {
        prepareUpdate();
        assertUpdate(ApiLifecycleState.ARCHIVED, CREATED, true);
        assertUpdate(ApiLifecycleState.ARCHIVED, PUBLISHED, true);
        assertUpdate(ApiLifecycleState.ARCHIVED, UNPUBLISHED, true);
        assertUpdate(ApiLifecycleState.ARCHIVED, DEPRECATED, true);
    }

    @Test
    public void shouldTraceReviewReject() throws TechnicalException {
        prepareReviewAuditTest();

        final ReviewEntity reviewEntity = new ReviewEntity();
        reviewEntity.setMessage("Test Review msg");
        apiService.rejectReview(API_ID, USER_NAME, reviewEntity);

        verify(auditService)
            .createApiAuditLog(
                argThat(apiId -> apiId.equals(API_ID)),
                anyMap(),
                argThat(evt -> Workflow.AuditEvent.API_REVIEW_REJECTED.equals(evt)),
                any(),
                any(),
                any()
            );
    }

    @Test
    public void shouldTraceReviewAsked() throws TechnicalException {
        prepareReviewAuditTest();

        when(roleService.findByScope(RoleScope.API)).thenReturn(Collections.singletonList(mock(RoleEntity.class)));
        final RolePermissionAction[] acls = { RolePermissionAction.UPDATE };
        when(roleService.hasPermission(any(), eq(ApiPermission.REVIEWS), eq(acls))).thenReturn(true);

        MembershipEntity membershipEntity = mock(MembershipEntity.class);
        when(membershipEntity.getMemberType()).thenReturn(MembershipMemberType.USER);
        when(membershipEntity.getMemberId()).thenReturn("reviewerID");
        when(membershipService.getMembershipsByReferenceAndRole(eq(MembershipReferenceType.API), eq(API_ID), any()))
            .thenReturn(Sets.newSet(membershipEntity));

        UserEntity reviewerEntity = mock(UserEntity.class);
        when(reviewerEntity.getEmail()).thenReturn("Reviewer@ema.il");
        when(userService.findById("reviewerID")).thenReturn(reviewerEntity);

        final ReviewEntity reviewEntity = new ReviewEntity();
        reviewEntity.setMessage("Test Review msg");
        apiService.askForReview(API_ID, USER_NAME, reviewEntity);
        verify(auditService)
            .createApiAuditLog(
                argThat(apiId -> apiId.equals(API_ID)),
                anyMap(),
                argThat(evt -> Workflow.AuditEvent.API_REVIEW_ASKED.equals(evt)),
                any(),
                any(),
                any()
            );
        verify(emailService)
            .sendAsyncEmailNotification(
                argThat(
                    emailNotification ->
                        emailNotification
                            .getTemplate()
                            .equals(EmailNotificationBuilder.EmailTemplate.API_ASK_FOR_REVIEW.getLinkedHook().getTemplate()) &&
                        emailNotification.getTo().length == 1 &&
                        emailNotification.getTo()[0].equals("Reviewer@ema.il")
                ),
                any()
            );
        verify(roleService).findByScope(RoleScope.API);
    }

    @Test
    public void shouldTraceReviewAccepted() throws TechnicalException {
        prepareReviewAuditTest();

        final ReviewEntity reviewEntity = new ReviewEntity();
        reviewEntity.setMessage("Test Review msg");
        apiService.acceptReview(API_ID, USER_NAME, reviewEntity);
        verify(auditService)
            .createApiAuditLog(
                argThat(apiId -> apiId.equals(API_ID)),
                anyMap(),
                argThat(evt -> Workflow.AuditEvent.API_REVIEW_ACCEPTED.equals(evt)),
                any(),
                any(),
                any()
            );
    }

    private void prepareReviewAuditTest() throws TechnicalException {
        api.setDefinition(API_DEFINITION);
        when(apiRepository.findById(API_ID)).thenReturn(Optional.of(api));

        final MembershipEntity membership = new MembershipEntity();
        membership.setMemberId(USER_NAME);
        when(membershipService.getPrimaryOwner(GraviteeContext.getCurrentOrganization(), MembershipReferenceType.API, API_ID))
            .thenReturn(membership);

        when(userService.findById(USER_NAME)).thenReturn(mock(UserEntity.class));

        final Workflow workflow = new Workflow();
        workflow.setState(WorkflowState.REQUEST_FOR_CHANGES.name());
        when(workflowService.create(any(), any(), any(), any(), any(), any())).thenReturn(workflow);
    }

    @Test
    public void shouldNotChangeLifecycleStateFromCreatedInReview() throws TechnicalException {
        prepareUpdate();
        when(parameterService.findAsBoolean(Key.API_REVIEW_ENABLED, "DEFAULT", ParameterReferenceType.ENVIRONMENT)).thenReturn(true);
        final Workflow workflow = new Workflow();
        workflow.setState("IN_REVIEW");
        when(workflowService.findByReferenceAndType(API, API_ID, REVIEW)).thenReturn(singletonList(workflow));

        assertUpdate(ApiLifecycleState.CREATED, CREATED, false);
        assertUpdate(ApiLifecycleState.CREATED, PUBLISHED, true);
        assertUpdate(ApiLifecycleState.CREATED, UNPUBLISHED, true);
        assertUpdate(ApiLifecycleState.CREATED, DEPRECATED, true);
    }

    @Test(expected = AllowOriginNotAllowedException.class)
    public void shouldHaveAllowOriginNotAllowed() throws TechnicalException {
        prepareUpdate();
        updateApiEntity.getProxy().getCors().setEnabled(true);
        updateApiEntity
            .getProxy()
            .getCors()
            .setAccessControlAllowOrigin(
                Sets.newSet(
                    "http://example.com",
                    "localhost",
                    "https://10.140.238.25:8080",
                    "(http|https)://[a-z]{6}.domain.[a-zA-Z]{2,6}",
                    ".*.company.com",
                    "/test^"
                )
            );
        apiService.update(API_ID, updateApiEntity);
    }

    @Test
    public void shouldHaveAllowOriginWildcardAllowed() throws TechnicalException {
        prepareUpdate();
        updateApiEntity.getProxy().getCors().setEnabled(true);
        updateApiEntity.getProxy().getCors().setAccessControlAllowOrigin(Collections.singleton("*"));
        apiService.update(API_ID, updateApiEntity);
    }

    @Test
    public void shouldHaveAllowOriginNullAllowed() throws TechnicalException {
        prepareUpdate();
        updateApiEntity.getProxy().getCors().setEnabled(true);
        updateApiEntity.getProxy().getCors().setAccessControlAllowOrigin(Collections.singleton("null"));
        apiService.update(API_ID, updateApiEntity);
    }

    @Test
    public void shouldCreateAuditApiLoggingDisabledWhenSwitchingLogging() throws TechnicalException {
        when(parameterService.findAsBoolean(Key.LOGGING_AUDIT_TRAIL_ENABLED, ParameterReferenceType.ENVIRONMENT)).thenReturn(true);

        api.setDefinition(
            "{\"id\": \"" +
            API_ID +
            "\",\"name\": \"" +
            API_NAME +
            "\"," +
            "   \"proxy\": {" +
            "    \"logging\": {\n" +
            "      \"mode\":\"CLIENT_PROXY\",\n" +
            "      \"condition\":\"condition\"\n" +
            "    },\n" +
            "\"context_path\": \"/old\"}}"
        );
        updatedApi.setDefinition("{\"id\": \"" + API_ID + "\",\"name\": \"" + API_NAME + "\",\"proxy\": {\"context_path\": \"/old\"}}");

        prepareUpdate();

        updateApiEntity.getProxy().setLogging(null);

        apiService.update(API_ID, updateApiEntity);

        verify(auditService)
            .createApiAuditLog(eq(API_ID), eq(emptyMap()), eq(Api.AuditEvent.API_LOGGING_DISABLED), any(Date.class), any(), any());
    }

    @Test
    public void shouldCreateAuditApiLoggingEnabledWhenSwitchingLogging() throws TechnicalException {
        when(parameterService.findAsBoolean(Key.LOGGING_AUDIT_TRAIL_ENABLED, ParameterReferenceType.ENVIRONMENT)).thenReturn(true);

        api.setDefinition("{\"id\": \"" + API_ID + "\",\"name\": \"" + API_NAME + "\",\"proxy\": {\"context_path\": \"/old\"}}");
        updatedApi.setDefinition(
            "{\"id\": \"" +
            API_ID +
            "\",\"name\": \"" +
            API_NAME +
            "\"," +
            "   \"proxy\": {" +
            "    \"logging\": {\n" +
            "      \"mode\":\"CLIENT_PROXY\",\n" +
            "      \"condition\":\"condition\"\n" +
            "    },\n" +
            "\"context_path\": \"/old\"}}"
        );

        prepareUpdate();

        updateApiEntity.getProxy().setLogging(null);

        apiService.update(API_ID, updateApiEntity);

        verify(auditService)
            .createApiAuditLog(eq(API_ID), eq(emptyMap()), eq(Api.AuditEvent.API_LOGGING_ENABLED), any(Date.class), any(), any());
    }

    @Test
    public void shouldCreateAuditApiLoggingUpdatedWhenSwitchingLogging() throws TechnicalException {
        when(parameterService.findAsBoolean(Key.LOGGING_AUDIT_TRAIL_ENABLED, ParameterReferenceType.ENVIRONMENT)).thenReturn(true);

        api.setDefinition(
            "{\"id\": \"" +
            API_ID +
            "\",\"name\": \"" +
            API_NAME +
            "\"," +
            "   \"proxy\": {" +
            "    \"logging\": {\n" +
            "      \"mode\":\"CLIENT_PROXY\",\n" +
            "      \"condition\":\"condition\"\n" +
            "    },\n" +
            "\"context_path\": \"/old\"}}"
        );
        updatedApi.setDefinition(
            "{\"id\": \"" +
            API_ID +
            "\",\"name\": \"" +
            API_NAME +
            "\"," +
            "   \"proxy\": {" +
            "    \"logging\": {\n" +
            "      \"mode\":\"CLIENT_PROXY\",\n" +
            "      \"condition\":\"condition2\"\n" +
            "    },\n" +
            "\"context_path\": \"/old\"}}"
        );

        prepareUpdate();

        updateApiEntity.getProxy().setLogging(null);

        apiService.update(API_ID, updateApiEntity);

        verify(auditService)
            .createApiAuditLog(eq(API_ID), eq(emptyMap()), eq(Api.AuditEvent.API_LOGGING_UPDATED), any(Date.class), any(), any());
    }

    private void assertUpdate(
        final ApiLifecycleState fromLifecycleState,
        final io.gravitee.rest.api.model.api.ApiLifecycleState lifecycleState,
        final boolean shouldFail
    ) {
        api.setApiLifecycleState(fromLifecycleState);
        updateApiEntity.setLifecycleState(lifecycleState);
        boolean failed = false;
        try {
            apiService.update(API_ID, updateApiEntity);
        } catch (final LifecycleStateChangeNotAllowedException ise) {
            failed = true;
        }
        if (!failed && shouldFail) {
            fail("Should not be possible to change the lifecycle state of a " + fromLifecycleState + " API to " + lifecycleState);
        }
    }
}
