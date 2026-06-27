package com.interstellar.admin.domain.system;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interstellar.admin.common.AdminAudit;
import com.interstellar.admin.common.AdminPageResult;
import com.interstellar.admin.common.AdminQueryDTO;
import com.interstellar.admin.common.AdminQueryBuilder;
import com.interstellar.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/admin/versions")
@Tag(name = "版本管理", description = "客户端版本管理与更新检查")
public class AdminAppVersionController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ======================== 管理端 CRUD ========================

    @GetMapping
    @Operation(summary = "版本列表（管理端）")
    public Result<AdminPageResult> list(
            @Parameter(description = "页码") @RequestParam(value = "page", defaultValue = "1") int page,
            @Parameter(description = "每页条数") @RequestParam(value = "size", defaultValue = "20") int size,
            @Parameter(description = "平台筛选") @RequestParam(value = "platform", required = false) String platform,
            @Parameter(description = "状态筛选 0草稿 1发布") @RequestParam(value = "status", required = false) Integer status) {

        AdminQueryDTO query = new AdminQueryDTO();
        query.setPage(page);
        query.setSize(size);

        String columns = "id, version_code, build_number, platform, title, changelog, download_url, file_size, force_update, status, min_version, created_at, updated_at";
        AdminQueryBuilder builder = AdminQueryBuilder.from("app_version", columns);

        if (platform != null && !platform.isBlank()) {
            builder.where("platform = ?", platform);
        }
        builder.eq("status", status);
        builder.orderBy("build_number DESC, created_at DESC");

        Long total = jdbcTemplate.queryForObject(builder.buildCount(), Long.class, builder.buildParams());
        List<Map<String, Object>> records = jdbcTemplate.queryForList(builder.buildSelect(query), builder.buildParams());

        // 反序列化 changelog JSON 字符串为 List
        for (Map<String, Object> record : records) {
            parseChangelog(record);
        }

        return Result.success(new AdminPageResult(total != null ? total : 0, records, page, size));
    }

    @PostMapping
    @AdminAudit(action = "create_version", targetType = "version", description = "创建版本")
    @Operation(summary = "创建版本")
    public Result<String> create(@RequestBody Map<String, Object> body) {
        String versionCode = (String) body.get("versionCode");
        Object buildNumberObj = body.get("buildNumber");
        String platform = (String) body.getOrDefault("platform", "all");
        String title = (String) body.get("title");
        Object changelog = body.get("changelog");
        String downloadUrl = (String) body.get("downloadUrl");
        Object fileSizeObj = body.get("fileSize");
        Object forceUpdateObj = body.getOrDefault("forceUpdate", 0);
        Object statusObj = body.getOrDefault("status", 1);
        String minVersion = (String) body.get("minVersion");

        if (versionCode == null || versionCode.isBlank()) return Result.fail("版本号不能为空");
        if (buildNumberObj == null) return Result.fail("构建号不能为空");
        if (title == null || title.isBlank()) return Result.fail("标题不能为空");

        int buildNumber = ((Number) buildNumberObj).intValue();
        int forceUpdate = ((Number) forceUpdateObj).intValue();
        int status = ((Number) statusObj).intValue();
        Long fileSize = fileSizeObj != null ? ((Number) fileSizeObj).longValue() : null;

        String changelogJson;
        try {
            changelogJson = objectMapper.writeValueAsString(changelog != null ? changelog : Collections.emptyList());
        } catch (Exception e) {
            return Result.fail("更新日志格式错误");
        }

        jdbcTemplate.update(
                "INSERT INTO app_version (version_code, build_number, platform, title, changelog, download_url, file_size, force_update, status, min_version) VALUES (?,?,?,?,?,?,?,?,?,?)",
                versionCode, buildNumber, platform, title, changelogJson,
                downloadUrl, fileSize, forceUpdate, status, minVersion);

        return Result.successMessage("创建成功");
    }

    @PutMapping("/{id}")
    @AdminAudit(action = "update_version", targetType = "version", description = "更新版本")
    @Operation(summary = "更新版本")
    public Result<String> update(@PathVariable("id") Long id, @RequestBody Map<String, Object> body) {
        StringBuilder sql = new StringBuilder("UPDATE app_version SET ");
        List<Object> params = new ArrayList<>();

        if (body.containsKey("versionCode")) { sql.append("version_code = ?, "); params.add(body.get("versionCode")); }
        if (body.containsKey("buildNumber")) { sql.append("build_number = ?, "); params.add(((Number) body.get("buildNumber")).intValue()); }
        if (body.containsKey("platform")) { sql.append("platform = ?, "); params.add(body.get("platform")); }
        if (body.containsKey("title")) { sql.append("title = ?, "); params.add(body.get("title")); }
        if (body.containsKey("changelog")) {
            try {
                sql.append("changelog = ?, ");
                params.add(objectMapper.writeValueAsString(body.get("changelog")));
            } catch (Exception e) {
                return Result.fail("更新日志格式错误");
            }
        }
        if (body.containsKey("downloadUrl")) { sql.append("download_url = ?, "); params.add(body.get("downloadUrl")); }
        if (body.containsKey("fileSize")) { sql.append("file_size = ?, "); params.add(body.get("fileSize") != null ? ((Number) body.get("fileSize")).longValue() : null); }
        if (body.containsKey("forceUpdate")) { sql.append("force_update = ?, "); params.add(((Number) body.get("forceUpdate")).intValue()); }
        if (body.containsKey("status")) { sql.append("status = ?, "); params.add(((Number) body.get("status")).intValue()); }
        if (body.containsKey("minVersion")) { sql.append("min_version = ?, "); params.add(body.get("minVersion")); }

        if (params.isEmpty()) return Result.fail("无更新内容");

        sql.append("updated_at = NOW() WHERE id = ?");
        params.add(id);

        jdbcTemplate.update(sql.toString(), params.toArray());
        return Result.successMessage("更新成功");
    }

    @DeleteMapping("/{id}")
    @AdminAudit(action = "delete_version", targetType = "version", description = "删除版本")
    @Operation(summary = "删除版本")
    public Result<String> delete(@PathVariable("id") Long id) {
        jdbcTemplate.update("DELETE FROM app_version WHERE id = ?", id);
        return Result.successMessage("删除成功");
    }

    @PutMapping("/{id}/status")
    @AdminAudit(action = "toggle_version_status", targetType = "version", description = "版本上下架")
    @Operation(summary = "切换版本状态")
    public Result<String> toggleStatus(@PathVariable("id") Long id) {
        List<Map<String, Object>> versions = jdbcTemplate.queryForList("SELECT status FROM app_version WHERE id = ?", id);
        if (versions.isEmpty()) return Result.fail("版本不存在");

        Integer current = (Integer) versions.get(0).get("status");
        int newStatus = (current != null && current == 1) ? 0 : 1;
        jdbcTemplate.update("UPDATE app_version SET status = ?, updated_at = NOW() WHERE id = ?", newStatus, id);

        return Result.successMessage(newStatus == 1 ? "已发布" : "已下架");
    }

    // ======================== 客户端接口（免鉴权） ========================

    @GetMapping("/latest")
    @Operation(summary = "获取最新版本（客户端）")
    public Result<Map<String, Object>> getLatest(
            @Parameter(description = "客户端平台 android/ios") @RequestParam("platform") String platform,
            @Parameter(description = "客户端当前构建号") @RequestParam("buildNumber") int buildNumber) {

        List<Map<String, Object>> versions = jdbcTemplate.queryForList(
                "SELECT * FROM app_version WHERE status = 1 AND (platform = ? OR platform = 'all') ORDER BY build_number DESC LIMIT 1",
                platform);

        if (versions.isEmpty()) {
            return Result.success(Map.of("hasUpdate", false));
        }

        Map<String, Object> latest = versions.get(0);
        parseChangelog(latest);

        int latestBuildNumber = ((Number) latest.get("build_number")).intValue();
        boolean hasUpdate = latestBuildNumber > buildNumber;

        Integer forceUpdateFlag = (Integer) latest.get("force_update");
        String minVersion = (String) latest.get("min_version");
        boolean forceUpdate = forceUpdateFlag != null && forceUpdateFlag == 1;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("hasUpdate", hasUpdate);
        result.put("forceUpdate", forceUpdate);
        result.put("latestVersion", latest);

        return Result.success(result);
    }

    @GetMapping("/history")
    @Operation(summary = "版本历史列表（客户端）")
    public Result<AdminPageResult> getHistory(
            @Parameter(description = "页码") @RequestParam(value = "page", defaultValue = "1") int page,
            @Parameter(description = "每页条数") @RequestParam(value = "size", defaultValue = "20") int size) {

        AdminQueryDTO query = new AdminQueryDTO();
        query.setPage(page);
        query.setSize(size);

        String columns = "id, version_code, build_number, platform, title, changelog, download_url, file_size, force_update, status, min_version, created_at";
        AdminQueryBuilder builder = AdminQueryBuilder.from("app_version", columns);
        builder.where("status = ?", 1);
        builder.orderBy("build_number DESC");

        Long total = jdbcTemplate.queryForObject(builder.buildCount(), Long.class, builder.buildParams());
        List<Map<String, Object>> records = jdbcTemplate.queryForList(builder.buildSelect(query), builder.buildParams());

        for (Map<String, Object> record : records) {
            parseChangelog(record);
        }

        return Result.success(new AdminPageResult(total != null ? total : 0, records, page, size));
    }

    // ======================== 工具方法 ========================

    /**
     * 将 changelog 字段从 JSON 字符串反序列化为 List<String>
     */
    private void parseChangelog(Map<String, Object> record) {
        Object changelog = record.get("changelog");
        if (changelog instanceof String) {
            try {
                List<String> list = objectMapper.readValue((String) changelog, new TypeReference<List<String>>() {});
                record.put("changelog", list);
            } catch (Exception e) {
                record.put("changelog", Collections.emptyList());
            }
        }
    }
}
