package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.injector.methods.SelectByIds;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.UserHolder;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {
    @Resource
    StringRedisTemplate stringRedisTemplate;

    @Resource
    IUserService userService;

    @Override
    public Result isFollowed(Long id) {
        UserDTO user = UserHolder.getUser();
        String key = RedisConstants.FOLLOW_KEY + user.getId();
        Boolean member = stringRedisTemplate.opsForSet().isMember(key, id.toString());
        return Result.ok(member);
    }

    @Override
    public Result followUser(Long id, Boolean isFollow) {
        UserDTO user = UserHolder.getUser();
        String key = RedisConstants.FOLLOW_KEY + user.getId();
        if (isFollow) {
            Follow follow = new Follow();
            follow.setFollowUserId(id);
            follow.setUserId(user.getId());
            boolean save = save(follow);
            if (save) {
                stringRedisTemplate.opsForSet().add(key, id.toString());
            }
        } else {
            boolean remove = remove(new QueryWrapper<Follow>().eq("user_id", user.getId()).eq("follow_user_id", id));
            if (remove) {
                stringRedisTemplate.opsForSet().remove(key, id.toString());
            }
        }
        return Result.ok();
    }

    @Override
    public Result commonFollowList(Long id) {
        UserDTO user = UserHolder.getUser();
        String key = RedisConstants.FOLLOW_KEY + user.getId();
        String key2 = RedisConstants.FOLLOW_KEY + id;
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(key, key2);
        if (intersect == null || intersect.isEmpty()) {
            return Result.ok();
        }
        List<Long> ids = intersect.stream().map(Long::valueOf).toList();
        List<UserDTO> userDTOS = userService.listByIds(ids).stream().map(u -> BeanUtil.copyProperties(u, UserDTO.class)).toList();
        return Result.ok(userDTOS);
    }
}
