package com.bit.lot.flower.auth.store.filter;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bit.lot.flower.auth.common.util.JwtUtil;
import com.bit.lot.flower.auth.common.util.RedisBlackListTokenUtil;
import com.bit.lot.flower.auth.common.util.RedisRefreshTokenUtil;
import com.bit.lot.flower.auth.store.entity.StoreManagerAuth;
import com.bit.lot.flower.auth.store.exception.StoreManagerAuthException;
import com.bit.lot.flower.auth.store.http.filter.StoreMangerAuthorizationFilter;
import com.bit.lot.flower.auth.store.repository.StoreManagerAuthRepository;
import com.bit.lot.flower.auth.store.valueobject.StoreManagerStatus;
import io.jsonwebtoken.MalformedJwtException;
import java.util.Map;
import javax.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.RedisKeyValueAdapter;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.util.NestedServletException;

@TestPropertySource(locations="classpath:application-test.yml")
@ActiveProfiles("test")
@Transactional
@ExtendWith(SpringExtension.class)
@SpringBootTest
public class StoreManagerAuthorizationFilterTest {

  Long storeManagerPk = 1L;
  @Value("${store.manager.id}")
  String email = "id";
  @Value("${store.manager.password}")
  String password;
  @Autowired
  StoreManagerAuthRepository repository;
  @Autowired
  StoreMangerAuthorizationFilter authorizationFilter;
  @Autowired
  WebApplicationContext webApplicationContext;
  @MockBean
  RedisTemplate<Object, Object> redisTemplate;
  @MockBean
  RedisBlackListTokenUtil redisBlackListTokenUtil;
  @MockBean
  RedisKeyValueAdapter keyValueAdapter;

  MockMvc mvc;
  final String claimRoleName = "ROLE";


  @BeforeEach
  void setUp() {
    mvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).addFilter(authorizationFilter)
        .build();
  }

  private void saveValidStoreManagerUser() {
    repository.save(StoreManagerAuth.builder().email(email).password(password).id(storeManagerPk)
        .status(StoreManagerStatus.ROLE_STORE_MANAGER_PERMITTED).lastLogoutTime(null).build());
  }

  private String createUnValidToken() {
    return "unValidRandomToken";
  }

  private String createValidToken(StoreManagerStatus storeManagerStatus) {
    Map<String, Object> claimMap = JwtUtil.addClaims(claimRoleName,
        storeManagerStatus);
    return JwtUtil.generateAccessTokenWithClaims(String.valueOf(storeManagerPk), claimMap);
  }

  private MvcResult requestWithStatusToken(StoreManagerStatus storeManagerStatus)
      throws Exception {
    return mvc.perform(MockMvcRequestBuilders.post("/stores/logout")
        .header("Authorization", "Bearer " + createValidToken(storeManagerStatus))).andReturn();
  }

  private MvcResult requestWithoutStatusToken()
      throws Exception {
    return mvc.perform(MockMvcRequestBuilders.post("/stores/logout")
            .header("Authorization", "Bearer " + createUnValidToken()))
        .andExpect(MockMvcResultMatchers.status().isOk()).andReturn();
  }

  private MvcResult requestWithNoTokenAtHeader()
      throws Exception {
    return mvc.perform(MockMvcRequestBuilders.post("/stores/logout"))
        .andExpect(MockMvcResultMatchers.status().is4xxClientError())
        .andReturn();
  }


  /**
   * @throws IllegalArgumentException 해당 error를 해당 Filter에서 try catch하지 않는 이유는 해당 error를
   *                                  JwtAuthetnicationFitler에서 잡아주기 때문이다. JwtAuthenticationFilter를
   *                                  거치지 않고 해당 Filter를 거치지 않는 경우는 없다.
   */

  @DisplayName("스토어 매니저 JWT토큰이 존재하지 않을 때 IllegalArgumentException catch")
  @Test
  void StoreManagerTokenAuthorizationTest_WhenTokenIsNotExist_CatchIllegalArgumentException() {
    assertThrows(IllegalArgumentException.class, () -> {
      requestWithNoTokenAtHeader();
    });
  }

  /**
   * JwtUtil.generateAccessToken(testUserId) 해당 코드를 작성하는 이유는 accesskey를 등록하기 위해서이다. accesskey를 등록하지
   * 않으면 무조건 IllegalArgumentException이 전파된다.
   */

  @DisplayName("토큰이 발급된 이후 JWT토큰이 존재하지 않을 때 MalformedJwtException catch")
  @Test
  void StoreManagerTokenAuthorizationTest_WhenTokenIsExistAfterLoginAndAccessKeyExist_ThrowMalformedJwtException() {
    JwtUtil.generateAccessToken(email);
    assertThrows(NullPointerException.class, () -> {
      requestWithoutStatusToken();
    });
  }

  @DisplayName("스토어매니저 상태 Pending일 경우 Throw StoreManagerAuthException")
  @Test
  void StoreManagerTokenAuthorizationTest_WhenStoreManagerUserIsPending_ThrowStoreManagerAuthException()
      throws Exception {
    assertThrowsExactly(NullPointerException.class,
        () -> {
          requestWithStatusToken(StoreManagerStatus.ROLE_STORE_MANAGER_PENDING);
        });
  }

  @DisplayName("스토어매니저 상태 Denined일 경우 Throw StoreManagerAuthException")
  @Test
  void StoreManagerTokenAuthorizationTest_WhenStoreManagerUserIsDenied_ThrowStoreManagerAuthException()
      throws Exception {
    assertThrowsExactly(NullPointerException.class,
        () -> {
          requestWithStatusToken(StoreManagerStatus.ROLE_STORE_MANAGER_DENIED);
        });
  }

  @DisplayName("스토어매니저 상태 Permitted일 경우 status 200")
  @Test
  void StoreManagerTokenAuthorizationTest_WhenStoreManagerUserIsPermitted_Status200()
      throws Exception {
    saveValidStoreManagerUser();
    assertThrows(NullPointerException.class, () -> {
      requestWithStatusToken(StoreManagerStatus.ROLE_STORE_MANAGER_PERMITTED).getResponse();
    });

  }


}
