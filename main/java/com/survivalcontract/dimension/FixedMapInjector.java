package com.survivalcontract.dimension;

import com.survivalcontract.SurvivalContract;
import net.minecraft.server.MinecraftServer;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 固定地图注入工具（从大厅地图机制中抽象，供大厅与附属玩法共用）。
 *
 * <p>把某个自定义维度（namespace/dimId）的 region/entities/poi 下的 .mca 写入
 * {@code <world>/dimensions/<namespace>/<dimId>/}，三级优先：
 * <ol>
 *   <li>外部存档 zip（内含 region/entities/poi 的 .mca，支持嵌套文件夹）</li>
 *   <li>外部散装文件夹（直接放 region/entities/poi）</li>
 *   <li>mod JAR 内置资源</li>
 * </ol>
 * 必须在 {@code SERVER_STARTING}（维度加载前）调用。
 */
public final class FixedMapInjector {

	/** 需要填充的子目录 */
	public static final String[] MCA_SUBDIRS = {"region", "entities", "poi"};

	private FixedMapInjector() {}

	/**
	 * 注入固定地图。
	 *
	 * @param server           服务端（SERVER_STARTING 阶段）
	 * @param namespace        维度命名空间（如 "survival-contract" / "hell-kitchen"）
	 * @param dimId            维度 id（如 "lobby" / "kitchen"）
	 * @param jarResourcePrefix JAR 内资源前缀（形如 "data/<ns>/<dim>/"，末尾带 /）
	 * @param jarFiles         每个子目录（与 {@link #MCA_SUBDIRS} 同序）的 JAR 内置 .mca 文件名清单，可为 null
	 * @param zipOverride      外部覆盖用的存档 zip（可为 null）
	 * @param folderOverride   外部覆盖用的散装文件夹（可为 null）
	 * @param label            日志标识（如 "大厅地图" / "地狱厨房地图"）
	 * @return 是否全部写入成功并通过校验（写入失败会删除半截文件，不会留下空地图）
	 */
	public static boolean inject(MinecraftServer server, String namespace, String dimId,
	                             String jarResourcePrefix, String[][] jarFiles,
	                             Path zipOverride, Path folderOverride, String label) {
		Path worldRoot = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);
		Path dimDir = worldRoot.resolve("dimensions" + File.separator + namespace + File.separator + dimId);
		return injectInto(dimDir, jarResourcePrefix, jarFiles, zipOverride, folderOverride, label);
	}

	/**
	 * 注入到指定目录（可以是暂存目录，由调用方校验后再搬入正式位置）。
	 *
	 * @return 是否全部写入成功并通过 {@link #verify} 校验
	 */
	public static boolean injectInto(Path dimDir, String jarResourcePrefix, String[][] jarFiles,
	                                 Path zipOverride, Path folderOverride, String label) {
		SurvivalContract.LOGGER.info("Injecting {} region files into: {}", label, dimDir);
		try {
			Files.createDirectories(dimDir);

			// 1) 外部存档 zip
			if (zipOverride != null) {
				if (extractMcaFromZip(zipOverride, dimDir)) {
					SurvivalContract.LOGGER.info("{} replaced with custom save zip: {}", label, zipOverride.getFileName());
					return true;
				}
				SurvivalContract.LOGGER.warn("Custom zip had no usable .mca files, falling back: {}", zipOverride);
			}

			// 2) 外部散装文件夹
			try {
				if (folderOverride != null && copyFromFolder(folderOverride, dimDir)) {
					SurvivalContract.LOGGER.info("{} replaced with custom folder: {}", label, folderOverride);
					return true;
				}
			} catch (Exception e) {
				SurvivalContract.LOGGER.warn("Failed to copy custom folder for {}, falling back to bundled map", label, e);
			}

			// 3) JAR 内置资源
			boolean ok = true;
			if (jarFiles != null) {
				for (int i = 0; i < MCA_SUBDIRS.length && i < jarFiles.length; i++) {
					Path targetSub = dimDir.resolve(MCA_SUBDIRS[i]);
					Files.createDirectories(targetSub);
					ok = copyFromJarResources(jarResourcePrefix, MCA_SUBDIRS[i], jarFiles[i], targetSub, label) && ok;
				}
			}
			if (!ok || !verify(dimDir, jarResourcePrefix, jarFiles)) {
				SurvivalContract.LOGGER.error("{} 注入失败或校验不通过（mod jar 是否在游戏运行中被替换？）：{}", label, dimDir);
				return false;
			}
			SurvivalContract.LOGGER.info("{} region files installed successfully (bundled).", label);
			return true;
		} catch (Exception e) {
			SurvivalContract.LOGGER.error("Failed to inject {} region files", label, e);
			return false;
		}
	}

	/**
	 * 校验地图目录：清单里每个 .mca 都必须存在，且大小与 JAR 内置资源一致
	 * （资源不可读时返回 false —— 宁可报错，也不能把空文件当成地图用）。
	 */
	public static boolean verify(Path dimDir, String jarResourcePrefix, String[][] jarFiles) {
		if (jarFiles == null) return true;
		if (!Files.isDirectory(dimDir)) return false; // 目录不存在（如首次开局还没有快照）：静默返回
		boolean ok = true;
		for (int i = 0; i < MCA_SUBDIRS.length && i < jarFiles.length; i++) {
			for (String name : jarFiles[i]) {
				String resourcePath = jarResourcePrefix + MCA_SUBDIRS[i] + "/" + name;
				long expected = resourceSize(resourcePath);
				Path f = dimDir.resolve(MCA_SUBDIRS[i]).resolve(name);
				long actual = Files.isRegularFile(f) ? sizeOf(f) : -1L;
				if (expected < 0 || actual != expected) {
					SurvivalContract.LOGGER.warn("地图校验失败：{}/{} 期望 {} 字节，实际 {} 字节（资源读取{}）",
							MCA_SUBDIRS[i], name, expected, actual, expected < 0 ? "失败" : "正常");
					ok = false;
				}
			}
		}
		return ok;
	}

	private static long sizeOf(Path f) {
		try {
			return Files.size(f);
		} catch (Exception e) {
			return -1L;
		}
	}

	/** 读取 classpath 资源字节数（JAR 条目或开发环境内的散装文件；不可读返回 -1） */
	public static long resourceSize(String resourcePath) {
		try {
			java.net.URL url = FixedMapInjector.class.getClassLoader().getResource(resourcePath);
			if (url == null) return -1L;
			java.net.URLConnection conn = url.openConnection();
			if (conn instanceof java.net.JarURLConnection jc) {
				jc.setUseCaches(false);
				var entry = jc.getJarEntry();
				return entry == null ? -1L : entry.getSize();
			}
			return conn.getContentLengthLong();
		} catch (Exception e) {
			return -1L;
		}
	}

	/**
	 * 从 zip 提取全部 .mca 到对应子目录（region/entities/poi，按路径段识别；支持存档根目录多套一层文件夹）。
	 * 返回是否提取到至少一个文件。
	 */
	public static boolean extractMcaFromZip(Path zipPath, Path dimDir) {
		try (ZipFile zip = new ZipFile(zipPath.toFile())) {
			var entries = zip.entries();
			boolean any = false;
			while (entries.hasMoreElements()) {
				ZipEntry entry = entries.nextElement();
				if (entry.isDirectory()) continue;
				String name = entry.getName().replace('\\', '/');
				if (!name.toLowerCase().endsWith(".mca")) continue;
				String subdir = resolveMcaSubdir(name);
				if (subdir == null) continue;

				Path fileName = Path.of(name).getFileName();
				Path target = dimDir.resolve(subdir).resolve(fileName);
				Files.createDirectories(target.getParent());
				try (InputStream is = zip.getInputStream(entry);
					 OutputStream os = Files.newOutputStream(target,
							 StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
					is.transferTo(os);
				}
				any = true;
			}
			return any;
		} catch (Exception e) {
			SurvivalContract.LOGGER.error("Failed to read custom save zip: {}", zipPath, e);
			return false;
		}
	}

	/** 根据条目路径识别 .mca 所属子目录（region/entities/poi；根目录 r.X.Z.mca 归 region） */
	public static String resolveMcaSubdir(String entryName) {
		for (String seg : entryName.split("/")) {
			String s = seg.toLowerCase();
			if (s.equals("region") || s.equals("entities") || s.equals("poi")) return s;
		}
		String fileName = Path.of(entryName).getFileName().toString().toLowerCase();
		return fileName.matches("r\\.-?\\d+\\.-?\\d+\\.mca") ? "region" : null;
	}

	/** 从散装文件夹拷贝 region/entities/poi 下的 .mca。返回是否拷贝到至少一个文件。 */
	public static boolean copyFromFolder(Path srcDir, Path dimDir) throws IOException {
		boolean any = false;
		for (String subdir : MCA_SUBDIRS) {
			Path src = srcDir.resolve(subdir);
			if (!Files.isDirectory(src)) continue;
			try (var stream = Files.list(src)) {
				for (Path f : (Iterable<Path>) stream::iterator) {
					if (!Files.isRegularFile(f)) continue;
					if (!f.getFileName().toString().toLowerCase().endsWith(".mca")) continue;
					Path target = dimDir.resolve(subdir).resolve(f.getFileName());
					Files.createDirectories(target.getParent());
					Files.copy(f, target, StandardCopyOption.REPLACE_EXISTING);
					any = true;
				}
			}
		}
		return any;
	}

	/**
	 * 从 classpath 资源拷贝指定文件名的 .mca（JAR 与开发环境都可用）。
	 *
	 * @return 是否全部拷贝成功；读到的字节数与资源声明大小不符时删除半截文件并返回 false
	 */
	public static boolean copyFromJarResources(String resourcePrefix, String subdir, String[] filenames,
	                                           Path targetDir, String label) {
		ClassLoader cl = FixedMapInjector.class.getClassLoader();
		if (filenames == null) return true;
		boolean ok = true;
		for (String fileName : filenames) {
			String resourcePath = resourcePrefix + subdir + "/" + fileName;
			java.net.URL url = cl.getResource(resourcePath);
			if (url == null) {
				SurvivalContract.LOGGER.error("  [{}] 资源缺失：{}", label, resourcePath);
				ok = false;
				continue;
			}
			long expected = resourceSize(resourcePath);
			Path target = targetDir.resolve(fileName);
			long copied;
			try (InputStream is = url.openStream();
				 OutputStream os = Files.newOutputStream(target,
						 StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
				copied = is.transferTo(os);
			} catch (IOException e) {
				SurvivalContract.LOGGER.error("  [{}] 资源读取失败：{}", label, resourcePath, e);
				try {
					Files.deleteIfExists(target);
				} catch (IOException ignored) {}
				ok = false;
				continue;
			}
			if (expected >= 0 && copied != expected) {
				SurvivalContract.LOGGER.error("  [{}] 资源读取不完整：{}（期望 {} 字节，实际 {} 字节）",
						label, resourcePath, expected, copied);
				try {
					Files.deleteIfExists(target); // 不留半截文件冒充地图
				} catch (IOException ignored) {}
				ok = false;
				continue;
			}
			SurvivalContract.LOGGER.debug("  [{}] Injected: {}/{}", label, subdir, fileName);
		}
		return ok;
	}
}
