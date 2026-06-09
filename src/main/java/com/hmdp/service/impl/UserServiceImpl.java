package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Msg;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.*;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.asm.Advice;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.cache.CacheProperties;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private JwtUtils jwtUtils;

    @Resource
    RestTemplate restTemplate;

    @Value("${send.message.url}")
    String url;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        // TODO 发送短信验证码并保存验证码
        if (RegexUtils.isPhoneInvalid(phone)) {
            log.debug(phone);
            return Result.fail("电话号码格式错误！");
        }
        String s = RandomUtil.randomNumbers(6);
        restTemplate.getForObject(url, Msg.class, s, RedisConstants.LOGIN_CODE_TTL, phone);
        stringRedisTemplate.opsForValue().set(RedisConstants.LOGIN_CODE_KEY + phone, s, RedisConstants.LOGIN_CODE_TTL, TimeUnit.MINUTES);
        log.debug("code:{}", s);
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        // TODO 实现登录功能
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            log.debug(phone);
            return Result.fail("电话号码格式错误！");
        }
        String code = stringRedisTemplate.opsForValue().get(RedisConstants.LOGIN_CODE_KEY + phone);
        if (code == null || !code.equals(loginForm.getCode())) {
            return Result.fail("验证码错误！");
        }
        User user = query().eq("phone", phone).one();

        if (user == null) {
            user = createNewUser(phone);
        }
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userDTOMap = BeanUtil.beanToMap(
                userDTO, new HashMap<>(),
                CopyOptions.create().
                        setIgnoreNullValue(true).
                        setFieldValueEditor(
                                (fieldKey, fieldValue) -> fieldValue.toString()
                        ));
        String token = jwtUtils.createToken(userDTOMap, user.getId());
        log.info(user.toString());
        return Result.ok(token);
    }

    @Override
    public Result signCount() {
        Long userId = UserHolder.getUser().getId();
        LocalDate now = LocalDate.now();
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = RedisConstants.USER_SIGN_KEY + userId + keySuffix;

        int dayOfMonth = now.getDayOfMonth();
        List<Long> field = stringRedisTemplate.opsForValue().bitField(key,
                BitFieldSubCommands.create()
                        .get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth))
                        .valueAt(0));
        if (field == null || field.isEmpty()) {
            return Result.ok(0);
        }
        Long sign = field.getFirst();
        if (sign == null) {
            return Result.ok(0);
        }
        int count=0;
        while (true) {
            if ((sign & 1) == 0) {
                break;
            } else {
                count++;
            }
            sign >>= 1;
        }
        return Result.ok(count);
    }

    @Override
    public Result sign() {
        Long userId = UserHolder.getUser().getId();
        LocalDate now = LocalDate.now();
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = RedisConstants.USER_SIGN_KEY + userId + keySuffix;

        stringRedisTemplate.opsForValue().setBit(key, now.getDayOfMonth() - 1, true);
        return Result.ok();
    }

    public User createNewUser(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(SystemConstants.USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        save(user);
        return user;
    }
}
