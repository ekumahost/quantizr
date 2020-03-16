package org.subnode.service;

import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.Iterator;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.subnode.config.AppProp;
import org.subnode.model.client.NodeProp;
import org.subnode.config.SpringContextUtil;
import org.subnode.image.ImageUtil;
import org.subnode.mongo.CreateNodeLocation;
import org.subnode.mongo.MongoApi;
import org.subnode.mongo.MongoSession;
import org.subnode.model.client.PrivilegeType;
import org.subnode.mongo.model.SubNode;
import org.subnode.request.DeleteAttachmentRequest;
import org.subnode.request.UploadFromUrlRequest;
import org.subnode.response.DeleteAttachmentResponse;
import org.subnode.response.UploadFromUrlResponse;
import org.subnode.util.ExUtil;
import org.subnode.util.LimitedInputStream;
import org.subnode.util.LimitedInputStreamEx;
import org.subnode.util.MimeTypeUtils;
import org.subnode.util.MultipartFileSender;
import org.subnode.util.StreamUtil;
import org.subnode.util.ThreadLocals;
import org.subnode.util.ValContainer;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.AutoCloseInputStream;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/**
 * Service for managing node attachments.
 * 
 * Node attachments are binary attachments that the user can opload onto a node.
 * Each node allows either zero or one attachments. Uploading a new attachment
 * wipes out and replaces the previous attachment. If the attachment is an
 * 'image' type then it gets displayed right on the page. Otherwise a download
 * link is what gets displayed on the node.
 */
@Component
public class AttachmentService {
	private static final Logger log = LoggerFactory.getLogger(AttachmentService.class);

	@Autowired
	private MongoApi api;

	@Autowired
	private AppProp appProp;

	@Autowired
	private MimeTypeUtils mimeTypeUtils;

	/*
	 * Upload from User's computer. Standard HTML form-based uploading of a file
	 * from user machine
	 */
	public ResponseEntity<?> uploadMultipleFiles(MongoSession session, String nodeId, MultipartFile[] uploadFiles,
			boolean explodeZips, boolean toIpfs) {
		if (nodeId == null) {
			throw ExUtil.newEx("target nodeId not provided");
		}

		try {
			if (session == null) {
				session = ThreadLocals.getMongoSession();
			}

			/*
			 * OLD LOGIC: Uploading a single file attaches to the current node, but
			 * uploading multiple files creates each file on it's own subnode (child nodes)
			 */
			// boolean addAsChildren = countFileUploads(uploadFiles) > 1;

			/*
			 * NEW LOGIC: If the node itself currently has an attachment, leave it alone and
			 * just upload UNDERNEATH this current node.
			 */
			SubNode node = api.getNode(session, nodeId);
			if (node == null) {
				throw ExUtil.newEx("Node not found.");
			}

			api.auth(session, node, PrivilegeType.WRITE);

			boolean addAsChildren = uploadFiles.length > 1;
			int maxFileSize = 20 * 1024 * 1024;

			for (MultipartFile uploadFile : uploadFiles) {
				String fileName = uploadFile.getOriginalFilename();
				long size = uploadFile.getSize();
				if (!StringUtils.isEmpty(fileName)) {
					log.debug("Uploading file: " + fileName);

					LimitedInputStreamEx limitedIs = null;
					try {
						limitedIs = new LimitedInputStreamEx(uploadFile.getInputStream(), maxFileSize);
						attachBinaryFromStream(session, node, nodeId, fileName, size, limitedIs, null, -1, -1,
								addAsChildren, explodeZips, toIpfs);
					} finally {
						StreamUtil.close(limitedIs);
					}
				}
			}
			api.saveSession(session);
		} catch (Exception e) {
			log.error(e.getMessage());
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}

		return new ResponseEntity<>(HttpStatus.OK);
	}

	//
	// private int countFileUploads(MultipartFile[] uploadFiles) {
	// int count = 0;
	// for (MultipartFile uploadFile : uploadFiles) {
	// String fileName = uploadFile.getOriginalFilename();
	// if (!StringUtils.isEmpty(fileName)) {
	// count++;
	// }
	// }
	// return count;
	// }
	//

	/*
	 * Gets the binary attachment from a supplied stream and loads it into the
	 * repository on the node specified in 'nodeId'
	 */
	public void attachBinaryFromStream(MongoSession session, SubNode node, String nodeId, String fileName, long size,
			LimitedInputStreamEx is, String mimeType, int width, int height, boolean addAsChild, boolean explodeZips,
			boolean toIpfs) {

		/*
		 * If caller already has 'node' it can pass node, and avoid looking up node
		 * again
		 */
		if (node == null && nodeId != null) {
			node = api.getNode(session, nodeId);
		}

		api.auth(session, node, PrivilegeType.WRITE);

		/*
		 * Multiple file uploads always attach children for each file uploaded
		 */
		if (addAsChild) {
			try {
				SubNode newNode = api.createNode(session, node, null, null, null, CreateNodeLocation.LAST);
				newNode.setContent("### " + fileName);

				/*
				 * todo-1: saving multiple uploads isn't working right now. It's a work in
				 * progress. This isn't a bug, but just incomplete code.
				 */
				api.save(session, newNode);
				// api.saveSession(session);

				node = newNode;
			} catch (Exception ex) {
				throw ExUtil.newEx(ex);
			}
		}

		/* mimeType can be passed as null if it's not yet determined */
		if (mimeType == null) {
			mimeType = URLConnection.guessContentTypeFromName(fileName);
		}

		/*
		 * Hack/Fix for ms word. Not sure why the URLConnection fails for this, but it's
		 * new. I need to grab my old mime type map from legacy app and put in this
		 * project. Clearly the guessContentTypeFromName implementation provided by
		 * URLConnection has a screw loose.
		 */
		if (mimeType == null) {
			if (fileName.toLowerCase().endsWith(".doc")) {
				mimeType = "application/msword";
			}
		}

		/* fallback to at lest some acceptable mime type */
		if (mimeType == null) {
			mimeType = "application/octet-stream";
		}

		if (explodeZips && "application/zip".equalsIgnoreCase(mimeType)) {
			/*
			 * This is a prototype-scope bean, with state for processing one import at a
			 * time
			 */
			ImportZipService importZipStreamService = (ImportZipService) SpringContextUtil
					.getBean(ImportZipService.class);
			importZipStreamService.inputZipFileFromStream(session, is, node, false);
		} else {
			saveBinaryStreamToNode(session, is, mimeType, fileName, size, width, height, node, toIpfs);
		}
	}

	public void saveBinaryStreamToNode(MongoSession session, LimitedInputStreamEx inputStream, String mimeType,
			String fileName, long size, int width, int height, SubNode node, boolean toIpfs) {

		Long version = node.getIntProp(NodeProp.BIN_VER.s());
		if (version == null) {
			version = 0L;
		}

		/*
		 * NOTE: Setting this flag to false works just fine, and is more efficient, and
		 * will simply do everything EXCEPT calculate the image size
		 */
		boolean calcImageSize = true;

		BufferedImage bufImg = null;
		byte[] imageBytes = null;
		InputStream isTemp = null;
		int maxFileSize = 20 * 1024 * 1024;

		if (calcImageSize && ImageUtil.isImageMime(mimeType)) {
			LimitedInputStream is = null;
			try {
				is = new LimitedInputStreamEx(inputStream, maxFileSize);
				imageBytes = IOUtils.toByteArray(is);
				isTemp = new ByteArrayInputStream(imageBytes);
				bufImg = ImageIO.read(isTemp);

				try {
					node.setProp(NodeProp.IMG_WIDTH.s(), bufImg.getWidth());
					node.setProp(NodeProp.IMG_HEIGHT.s(), bufImg.getHeight());
				} catch (Exception e) {
					/*
					 * reading files from IPFS caused this exception, and I didn't investigate why
					 * yet, because I don't think it's a bug in my code, but something in IPFS.
					 */
					log.error("Failed to get image length.", e);
				}
			} catch (Exception e) {
				throw new RuntimeException(e);
			} finally {
				StreamUtil.close(is, isTemp);
			}
		}

		node.setProp(NodeProp.BIN_MIME.s(), mimeType);
		if (fileName != null) {
			node.setProp(NodeProp.BIN_FILENAME.s(), fileName);
		}

		log.debug("Uploading new BIN_VER: " + String.valueOf(version + 1));
		node.setProp(NodeProp.BIN_VER.s(), version + 1);

		if (imageBytes == null) {
			node.setProp(NodeProp.BIN_SIZE.s(), size);
			if (toIpfs) {
				api.writeStreamToIpfs(session, node, inputStream, null, mimeType, null);
			} else {
				api.writeStream(session, node, inputStream, null, mimeType, null);
			}
		} else {
			LimitedInputStream is = null;
			try {
				node.setProp(NodeProp.BIN_SIZE.s(), imageBytes.length);
				is = new LimitedInputStreamEx(new ByteArrayInputStream(imageBytes), maxFileSize);
				if (toIpfs) {
					api.writeStreamToIpfs(session, node, is, null, mimeType, null);
				} else {
					api.writeStream(session, node, is, null, mimeType, null);
				}
			} finally {
				StreamUtil.close(is);
			}
		}

		api.save(session, node);
	}

	/*
	 * Removes the attachment from the node specified in the request.
	 */
	public DeleteAttachmentResponse deleteAttachment(MongoSession session, DeleteAttachmentRequest req) {
		DeleteAttachmentResponse res = new DeleteAttachmentResponse();
		if (session == null) {
			session = ThreadLocals.getMongoSession();
		}
		String nodeId = req.getNodeId();
		SubNode node = api.getNode(session, nodeId);
		api.deleteBinary(session, node, null);
		deleteAllBinaryProperties(node);
		api.saveSession(session);
		res.setSuccess(true);
		return res;
	}

	/*
	 * Deletes all the binary-related properties from a node
	 */
	private void deleteAllBinaryProperties(SubNode node) {
		node.deleteProp(NodeProp.IMG_WIDTH.s());
		node.deleteProp(NodeProp.IMG_HEIGHT.s());
		node.deleteProp(NodeProp.BIN_MIME.s());
		node.deleteProp(NodeProp.BIN_FILENAME.s());
		node.deleteProp(NodeProp.BIN_SIZE.s());

		// NO! Do not delete binary version property. Browsers are allowed to cache
		// based on the URL of this node and this version.
		// What can happen if you ever delete BIN_VER is that it will reset the version
		// back to '1', and then when the user's browser
		// finds the URL with 'ver=1' it will display the OLD IMAGE (assuming it's an
		// image attachment). The way this would happen is a user uploads an image, then
		// deletes it
		// and then uploads another image. So really the places in the code where we
		// check for BIN_VER
		// to see if there's an attachment or not should be changed to look for BIN_MIME
		// instead.
		// node.deleteProp(NodeProp.BIN_VER);
	}

	/**
	 * Returns data for an attachment (Could be an image request, or any type of
	 * request for binary data from a node). This is the method that services all
	 * calls from the browser to get the data for the attachment to download/display
	 * the attachment.
	 * 
	 * the saga continues, after switching to InputStreamResouce images fail always
	 * with this error in js console::
	 * 
	 * InputStream has already been read - do not use InputStreamResource if a
	 * stream needs to be read multiple times
	 * 
	 * I stopped using this method (for now) because of this error, which is a
	 * Spring problem and not in my code. I created the simpler getBinary() version
	 * (below) which works find AND is simpler.
	 */
	public ResponseEntity<InputStreamResource> getBinary_legacy(MongoSession session, String nodeId) {
		try {
			if (session == null) {
				session = ThreadLocals.getMongoSession();
			}

			SubNode node = api.getNode(session, nodeId, false);
			boolean ipfs = StringUtils.isNotEmpty(node.getStringProp(NodeProp.IPFS_LINK.s()));

			// Everyone's account node can publish it's attachment and is assumed to be an
			// avatar.
			boolean allowAuth = true;
			if (api.isAnAccountNode(session, node)) {
				allowAuth = false;
			}

			if (allowAuth) {
				api.auth(session, node, PrivilegeType.READ);
			}

			String mimeTypeProp = node.getStringProp(NodeProp.BIN_MIME.s());
			if (mimeTypeProp == null) {
				throw ExUtil.newEx("unable to find mimeType property");
			}

			String fileName = node.getStringProp(NodeProp.BIN_FILENAME.s());
			if (fileName == null) {
				fileName = "filename";
			}

			// I took out the autoClosing stream, and I'm not sure if it's needed based on
			// current design, since when it
			// was originally put here.
			// AutoCloseInputStream acis = api.getAutoClosingStream(session, node, null,
			// allowAuth, ipfs);
			// StreamingResponseBody stream = (os) -> {
			// int bytesCopied = IOUtils.copy(acis, os);
			// log.debug("io copy complete: bytes="+bytesCopied);
			// os.flush();
			// log.debug("flush complete.");
			// };

			InputStream is = api.getStream(session, node, null, allowAuth, ipfs);
			InputStreamResource isr = new InputStreamResource(is);

			long size = node.getIntProp(NodeProp.BIN_SIZE.s());
			log.debug("Getting Binary for nodeId=" + nodeId + " size=" + size);

			/*
			 * To make, for example an image type of resource DISPLAY in the browser (rather
			 * than a downloaded file), you'd need this to be omitted (or 'inline')
			 */
			ResponseEntity.BodyBuilder builder = ResponseEntity.ok();
			if (size > 0) {
				/*
				 * todo-1: I'm getting the "disappearing image" network problem related to size
				 * (content length), but not calling 'contentLength()' below is a workaround.
				 * 
				 * You get this error if you just wait about 30s to 1 minute, and maybe scroll
				 * out of view and back into view the images.
				 * 
				 * Failed to load resource: net::ERR_CONTENT_LENGTH_MISMATCH
				 */

				builder = builder.contentLength(size);
			}
			builder = builder.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"");
			builder = builder.contentType(MediaType.parseMediaType(mimeTypeProp));
			return builder.body(isr);
		} catch (Exception e) {
			log.error(e.getMessage());
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}
	}

	public void getBinary(MongoSession session, String nodeId, HttpServletResponse response) {
		BufferedInputStream inStream = null;
		BufferedOutputStream outStream = null;

		try {
			if (session == null) {
				session = ThreadLocals.getMongoSession();
			}

			SubNode node = api.getNode(session, nodeId, false);
			boolean ipfs = StringUtils.isNotEmpty(node.getStringProp(NodeProp.IPFS_LINK.s()));

			// Everyone's account node can publish it's attachment and is assumed to be an
			// avatar.
			boolean allowAuth = true;
			if (api.isAnAccountNode(session, node)) {
				allowAuth = false;
			}

			if (allowAuth) {
				api.auth(session, node, PrivilegeType.READ);
			}

			String mimeTypeProp = node.getStringProp(NodeProp.BIN_MIME.s());
			if (mimeTypeProp == null) {
				throw ExUtil.newEx("unable to find mimeType property");
			}

			String fileName = node.getStringProp(NodeProp.BIN_FILENAME.s());
			if (fileName == null) {
				fileName = "filename";
			}

			InputStream is = api.getStream(session, node, null, allowAuth, ipfs);
			long size = node.getIntProp(NodeProp.BIN_SIZE.s());
			log.debug("Getting Binary for nodeId=" + nodeId + " size=" + size);

			response.setContentType(mimeTypeProp);
			response.setContentLength((int) size);
			response.setHeader("Content-Disposition", "attachment; filename=\"" + fileName + "\"");
			response.setHeader("Cache-Control", "public, max-age=31536000");

			inStream = new BufferedInputStream(is);
			outStream = new BufferedOutputStream(response.getOutputStream());

			IOUtils.copy(inStream, outStream);
			outStream.flush();
		} catch (Exception e) {
			log.error(e.getMessage());
		} finally {
			StreamUtil.close(inStream, outStream);
		}
	}

	/**
	 * Downloads a file by name that is expected to be in the Admin Data Folder
	 */
	public ResponseEntity<StreamingResponseBody> getFile(MongoSession session, String fileName, String disposition) {

		if (fileName.contains(".."))
			throw ExUtil.newEx("bad request.");

		try {
			String fullFileName = appProp.getAdminDataFolder() + File.separator + fileName;
			File file = new File(fullFileName);
			String checkPath = file.getCanonicalPath();
			/*
			 * todo-1: for better security make a REAL '/file/' folder under admin folder
			 * and assert that the file is in there directly
			 */
			if (!checkPath.startsWith(appProp.getAdminDataFolder()))
				throw ExUtil.newEx("bad request.");

			if (!file.isFile())
				throw ExUtil.newEx("file not found.");

			String mimeType = mimeTypeUtils.getMimeType(file);
			if (disposition == null) {
				disposition = "inline";
			}

			// I think we could be using the MultipartFileSender here, eventually but not
			// until we decople it from reading directly from filesystem
			AutoCloseInputStream acis = new AutoCloseInputStream(new FileInputStream(fullFileName));
			StreamingResponseBody stream = (os) -> {
				IOUtils.copy(acis, os);
				os.flush();
			};

			return ResponseEntity.ok()//
					.contentLength(file.length())//
					.header(HttpHeaders.CONTENT_DISPOSITION, disposition + "; filename=\"" + fileName + "\"")//
					.contentType(MediaType.parseMediaType(mimeType))//
					.body(stream);
		} catch (Exception ex) {
			throw ExUtil.newEx(ex);
		}
	}

	public ResponseEntity<StreamingResponseBody> getFileSystemResourceStream(MongoSession session, String nodeId,
			String disposition) {
		if (!session.isAdmin()) {
			throw new RuntimeException("unauthorized");
		}

		try {
			SubNode node = api.getNode(session, nodeId, false);
			if (node == null) {
				throw new RuntimeException("node not found: " + nodeId);
			}
			String fullFileName = node.getStringProp(NodeProp.FS_LINK);
			File file = new File(fullFileName);

			if (!file.exists() || !file.isFile()) {
				throw new RuntimeException("File not found: " + fullFileName);
			}

			String mimeType = mimeTypeUtils.getMimeType(file);
			if (disposition == null) {
				disposition = "inline";
			}

			/*
			 * I think we could be using the MultipartFileSender here, eventually but not
			 * until we decople it from reading directly from filesystem
			 */
			AutoCloseInputStream acis = new AutoCloseInputStream(new FileInputStream(fullFileName));

			/*
			 * I'm not sure if FileSystemResource is better than StreamingResponseBody, but
			 * i do know StreamingResponseBody does EXACTLY what is needed which is to use a
			 * small buffer size and never hold entire media file all in memory
			 */
			StreamingResponseBody stream = (os) -> {
				IOUtils.copy(acis, os);
				os.flush();
			};

			return ResponseEntity.ok()//
					.contentLength(file.length())//
					.header(HttpHeaders.CONTENT_DISPOSITION, disposition + "; filename=\"" + file.getName() + "\"")//
					.contentType(MediaType.parseMediaType(mimeType))//
					.body(stream);
		} catch (Exception ex) {
			throw ExUtil.newEx(ex);
		}
	}

	public void getFileSystemResourceStreamMultiPart(MongoSession session, String nodeId, String disposition,
			HttpServletRequest request, HttpServletResponse response) {
		try {
			SubNode node = api.getNode(session, nodeId, false);
			if (node == null) {
				throw new RuntimeException("node not found: " + nodeId);
			}

			api.auth(session, node, PrivilegeType.READ);

			String fullFileName = node.getStringProp(NodeProp.FS_LINK);
			File file = new File(fullFileName);

			if (!file.exists() || !file.isFile()) {
				throw new RuntimeException("File not found: " + fullFileName);
			}

			MultipartFileSender.fromPath(file.toPath()).with(request).with(response).withDisposition(disposition)
					.serveResource();
		} catch (Exception ex) {
			throw ExUtil.newEx(ex);
		}
	}

	/**
	 * Returns the seekable stream of the attachment data (assuming it's a
	 * streamable media type, like audio or video)
	 */
	public void getStreamMultiPart(MongoSession session, String nodeId, String disposition, HttpServletRequest request,
			HttpServletResponse response) {
		BufferedInputStream inStream = null;

		try {
			if (session == null) {
				session = ThreadLocals.getMongoSession();
			}

			SubNode node = api.getNode(session, nodeId, false);
			boolean ipfs = StringUtils.isNotEmpty(node.getStringProp(NodeProp.IPFS_LINK.s()));

			api.auth(session, node, PrivilegeType.READ);

			String mimeTypeProp = node.getStringProp(NodeProp.BIN_MIME.s());
			if (mimeTypeProp == null) {
				throw ExUtil.newEx("unable to find mimeType property");
			}

			String fileName = node.getStringProp(NodeProp.BIN_FILENAME.s());
			if (fileName == null) {
				fileName = "filename";
			}

			InputStream is = api.getStream(session, node, null, true, ipfs);
			long size = node.getIntProp(NodeProp.BIN_SIZE.s());
			inStream = new BufferedInputStream(is);

			MultipartFileSender.fromInputStream(inStream)//
					.with(request).with(response)//
					.withDisposition(disposition)//
					.withFileName("file-" + node.getId().toHexString())//
					.withLength(size)//
					// .withContentType(mimeTypeProp)//todo-0 removing this was a WAG, see if it
					// works with thsi back in
					.withLastModified(node.getModifyTime().getTime())//
					.serveResource();
		} catch (Exception e) {
			log.error(e.getMessage());
		} finally {
			// StreamUtil.close(inStream);
		}
	}

	/*
	 * Uploads an image attachment not from the user's machine but from some
	 * arbitrary internet URL they have provided, that could be pointing to an image
	 * or any other kind of content actually.
	 */
	public UploadFromUrlResponse readFromUrl(MongoSession session, UploadFromUrlRequest req) {
		UploadFromUrlResponse res = new UploadFromUrlResponse();
		readFromUrl(session, req.getSourceUrl(), req.getNodeId(), null, null);
		res.setSuccess(true);
		return res;
	}

	/**
	 * @param mimeHint This is an additional string invented because IPFS urls don't
	 *                 contain the file extension always and in that case we need to
	 *                 get it from the IPFS filename itself and that's what the hint
	 *                 is in that case. Normally however mimeHint is null
	 * 
	 *                 'inputStream' is admittely a retrofit to this function for
	 *                 when we want to just call this method and get an inputStream
	 *                 handed back that can be read from. Normally the inputStream
	 *                 ValContainer is null and not used.
	 */
	public void readFromUrl(MongoSession session, String sourceUrl, String nodeId, String mimeHint,
			ValContainer<InputStream> inputStream) {
		if (session == null) {
			session = ThreadLocals.getMongoSession();
		}
		String FAKE_USER_AGENT = "Mozilla/5.0";

		/*
		 * todo-2: This value exists in properties file, and also in TypeScript
		 * variable. Need to have better way to define this ONLY in properties file.
		 */
		int maxFileSize = 20 * 1024 * 1024;
		LimitedInputStreamEx limitedIs = null;

		try {
			URL url = new URL(sourceUrl);
			int timeout = 20;
			RequestConfig config = RequestConfig.custom()//
					.setConnectTimeout(timeout * 1000) //
					.setConnectionRequestTimeout(timeout * 1000) //
					.setSocketTimeout(timeout * 1000).build();

			String mimeType = URLConnection.guessContentTypeFromName(sourceUrl);
			if (StringUtils.isEmpty(mimeType) && mimeHint != null) {
				mimeType = URLConnection.guessContentTypeFromName(mimeHint);
			}

			/*
			 * if this is an image extension, handle it in a special way, mainly to extract
			 * the width, height from it
			 */
			if (ImageUtil.isImageMime(mimeType)) {
				/*
				 * DO NOT DELETE
				 *
				 * Basic version without masquerading as a web browser can cause a 403 error
				 * because some sites don't want just any old stream reading from them. Leave
				 * this note here as a warning and explanation
				 */
				// would restTemplate be better for this ?
				HttpClient client = HttpClientBuilder.create().setDefaultRequestConfig(config).build();
				HttpGet request = new HttpGet(sourceUrl);

				request.addHeader("User-Agent", FAKE_USER_AGENT);
				HttpResponse response = client.execute(request);
				log.debug("Response Code: " + response.getStatusLine().getStatusCode() + " reason="
						+ response.getStatusLine().getReasonPhrase());
				InputStream is = response.getEntity().getContent();

				limitedIs = new LimitedInputStreamEx(is, maxFileSize);

				if (inputStream != null) {
					inputStream.setVal(limitedIs);
				} else {
					// insert 0L for size now, because we don't know it yet
					attachBinaryFromStream(session, null, nodeId, sourceUrl, 0L, limitedIs, mimeType, -1, -1, false,
							false, false);
				}
			}
			/*
			 * if not an image extension, we can just stream directly into the database, but
			 * we want to try to get the mime type first, from calling detectImage so that
			 * if we do detect its an image we can handle it as one.
			 */
			else {
				if (!detectAndSaveImage(session, nodeId, sourceUrl, url, inputStream)) {
					HttpClient client = HttpClientBuilder.create().setDefaultRequestConfig(config).build();
					HttpGet request = new HttpGet(sourceUrl);
					request.addHeader("User-Agent", FAKE_USER_AGENT);
					HttpResponse response = client.execute(request);
					log.debug("Response Code: " + response.getStatusLine().getStatusCode() + " reason="
							+ response.getStatusLine().getReasonPhrase());
					InputStream is = response.getEntity().getContent();

					limitedIs = new LimitedInputStreamEx(is, maxFileSize);

					if (inputStream != null) {
						inputStream.setVal(limitedIs);
					} else {
						// insert 0L for size now, because we don't know it yet
						attachBinaryFromStream(session, null, nodeId, sourceUrl, 0L, limitedIs, "", -1, -1, false,
								false, false);
					}
				}
			}
		} catch (Exception e) {
			throw ExUtil.newEx(e);
		}
		/* finally block just for extra safety */
		finally {
			if (inputStream == null) {
				StreamUtil.close(limitedIs);
			}
		}

		if (inputStream == null) {
			api.saveSession(session);
		}
	}

	// FYI: Warning: this way of getting content type doesn't work.
	// String mimeType = URLConnection.guessContentTypeFromStream(inputStream);
	//
	/* returns true if it was detected AND saved as an image */
	private boolean detectAndSaveImage(MongoSession session, String nodeId, String fileName, URL url,
			ValContainer<InputStream> inputStream) {
		ImageInputStream is = null;
		LimitedInputStreamEx is2 = null;
		ImageReader reader = null;
		int maxFileSize = 20 * 1024 * 1024;

		try {
			is = ImageIO.createImageInputStream(url.openStream());
			Iterator<ImageReader> readers = ImageIO.getImageReaders(is);

			if (readers.hasNext()) {
				reader = readers.next();
				String formatName = reader.getFormatName();

				if (formatName != null) {
					formatName = formatName.toLowerCase();
					// log.debug("determined format name of image url: " + formatName);
					reader.setInput(is, true, false);
					BufferedImage bufImg = reader.read(0);
					String mimeType = "image/" + formatName;

					ByteArrayOutputStream os = new ByteArrayOutputStream();
					ImageIO.write(bufImg, formatName, os);
					byte[] bytes = os.toByteArray();
					is2 = new LimitedInputStreamEx(new ByteArrayInputStream(bytes), maxFileSize);

					if (inputStream != null) {
						inputStream.setVal(is2);
					} else {
						attachBinaryFromStream(session, null, nodeId, fileName, bytes.length, is2, mimeType,
								bufImg.getWidth(null), bufImg.getHeight(null), false, false, false);
					}
					return true;
				}
			}
		} catch (Exception e) {
			throw ExUtil.newEx(e);
		} finally {
			if (inputStream == null) {
				StreamUtil.close(is, is2, reader);
			}
		}

		return false;
	}
}
