# Redis 升级脚本 - Windows
# 必须以管理员身份运行此脚本

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Redis 升级脚本" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# 检查管理员权限
$isAdmin = ([Security.Principal.WindowsPrincipal] [Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $isAdmin) {
    Write-Host "错误: 请以管理员身份运行此脚本！" -ForegroundColor Red
    Write-Host "右键点击 PowerShell -> '以管理员身份运行'" -ForegroundColor Yellow
    exit 1
}

# 步骤 1: 停止 Redis 服务
Write-Host "[1/5] 停止 Redis 服务..." -ForegroundColor Yellow
try {
    Stop-Service -Name Redis -Force -ErrorAction Stop
    Start-Sleep -Seconds 2
    Write-Host "✓ Redis 服务已停止" -ForegroundColor Green
} catch {
    Write-Host "✗ 停止服务失败: $_" -ForegroundColor Red
    exit 1
}

# 步骤 2: 备份数据
Write-Host "[2/5] 备份 Redis 数据..." -ForegroundColor Yellow
$backupDir = "C:\Program Files\Redis\backup_$(Get-Date -Format 'yyyyMMdd_HHmmss')"
try {
    New-Item -ItemType Directory -Path $backupDir -Force | Out-Null
    Copy-Item "C:\Program Files\Redis\*.rdb" $backupDir -ErrorAction SilentlyContinue
    Copy-Item "C:\Program Files\Redis\*.conf" $backupDir -ErrorAction SilentlyContinue
    Copy-Item "C:\Program Files\Redis\*.aof" $backupDir -ErrorAction SilentlyContinue
    Write-Host "✓ 数据已备份到: $backupDir" -ForegroundColor Green
} catch {
    Write-Host "⚠ 备份警告: $_" -ForegroundColor Yellow
}

# 步骤 3: 下载最新版 Redis
Write-Host "[3/5] 准备下载 Redis 7.x..." -ForegroundColor Yellow
$downloadUrl = "https://github.com/redis-windows/redis-windows/releases/download/7.2.4/Redis-7.2.4-Windows-x64.zip"
$downloadPath = "$env:TEMP\Redis-7.2.4.zip"
$extractPath = "$env:TEMP\Redis-7.2.4"

Write-Host "下载地址: $downloadUrl" -ForegroundColor Cyan
Write-Host "提示: 如果下载失败，请手动从浏览器下载" -ForegroundColor Yellow
Write-Host "      https://github.com/redis-windows/redis-windows/releases/latest" -ForegroundColor Cyan

try {
    # 尝试下载（可能需要科学上网）
    Invoke-WebRequest -Uri $downloadUrl -OutFile $downloadPath -UseBasicParsing
    Write-Host "✓ 下载完成" -ForegroundColor Green
} catch {
    Write-Host "✗ 自动下载失败" -ForegroundColor Red
    Write-Host ""
    Write-Host "请手动执行以下步骤:" -ForegroundColor Yellow
    Write-Host "1. 打开浏览器访问: https://github.com/redis-windows/redis-windows/releases/latest" -ForegroundColor White
    Write-Host "2. 下载最新的 .zip 或 .msi 安装包" -ForegroundColor White
    Write-Host "3. 如果使用 .msi: 直接运行安装程序覆盖安装" -ForegroundColor White
    Write-Host "4. 如果使用 .zip: 解压并替换 C:\Program Files\Redis 目录下的文件" -ForegroundColor White
    Write-Host ""
    
    $manualInstall = Read-Host "是否已完成手动安装？(y/n)"
    if ($manualInstall -ne 'y') {
        exit 0
    }
}

# 步骤 4: 安装新版 Redis
Write-Host "[4/5] 安装新版 Redis..." -ForegroundColor Yellow

if (Test-Path $downloadPath) {
    try {
        # 解压
        Expand-Archive -Path $downloadPath -DestinationPath $extractPath -Force
        
        # 停止并卸载旧服务
        sc.exe delete Redis 2>$null
        
        # 复制新文件
        Get-ChildItem $extractPath -Recurse | ForEach-Object {
            $targetPath = Join-Path "C:\Program Files\Redis" $_.FullName.Substring($extractPath.Length)
            if ($_.PSIsContainer) {
                New-Item -ItemType Directory -Path $targetPath -Force | Out-Null
            } else {
                Copy-Item $_.FullName $targetPath -Force
            }
        }
        
        # 重新安装服务
        & "C:\Program Files\Redis\redis-server.exe" --service-install "C:\Program Files\Redis\redis.windows-service.conf" --loglevel verbose
        
        Write-Host "✓ 安装完成" -ForegroundColor Green
        
        # 清理临时文件
        Remove-Item $downloadPath -Force -ErrorAction SilentlyContinue
        Remove-Item $extractPath -Recurse -Force -ErrorAction SilentlyContinue
    } catch {
        Write-Host "✗ 安装失败: $_" -ForegroundColor Red
        Write-Host "请手动安装下载的压缩包" -ForegroundColor Yellow
    }
}

# 步骤 5: 启动服务并验证
Write-Host "[5/5] 启动 Redis 服务..." -ForegroundColor Yellow
try {
    Start-Service -Name Redis
    Start-Sleep -Seconds 2
    
    $version = & "C:\Program Files\Redis\redis-cli.exe" --version
    Write-Host "✓ Redis 服务已启动" -ForegroundColor Green
    Write-Host "✓ 当前版本: $version" -ForegroundColor Green
    
    if ($version -match "7\.") {
        Write-Host ""
        Write-Host "========================================" -ForegroundColor Green
        Write-Host "升级成功！现在支持 GEOSEARCH 命令" -ForegroundColor Green
        Write-Host "========================================" -ForegroundColor Green
    } else {
        Write-Host ""
        Write-Host "⚠ 版本可能不是 7.x，请检查" -ForegroundColor Yellow
    }
} catch {
    Write-Host "✗ 启动服务失败: $_" -ForegroundColor Red
    Write-Host "请尝试手动启动: Start-Service -Name Redis" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "按任意键退出..." -ForegroundColor Gray
$null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
