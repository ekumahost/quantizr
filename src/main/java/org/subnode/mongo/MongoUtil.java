package org.subnode.mongo;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Pattern;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexField;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.data.mongodb.core.index.TextIndexDefinition;
import org.springframework.data.mongodb.core.index.TextIndexDefinition.TextIndexDefinitionBuilder;
import org.springframework.stereotype.Component;
import org.subnode.config.AppProp;
import org.subnode.config.NodeName;
import org.subnode.model.client.NodeProp;
import org.subnode.model.client.NodeType;
import org.subnode.model.client.PrincipalName;
import org.subnode.model.client.PrivilegeType;
import org.subnode.mongo.model.AccessControl;
import org.subnode.mongo.model.SubNode;
import org.subnode.request.SignupRequest;
import org.subnode.service.AclService;
import org.subnode.service.UserManagerService;
import org.subnode.util.Const;
import org.subnode.util.Convert;
import org.subnode.util.ExUtil;
import org.subnode.util.ImageSize;
import org.subnode.util.ImageUtil;
import org.subnode.util.SubNodeUtil;
import org.subnode.util.Util;
import org.subnode.util.ValContainer;
import org.subnode.util.XString;

/**
 * Utilities related to management of the JCR Repository
 */
@Component
public class MongoUtil {
	private static final Logger log = LoggerFactory.getLogger(MongoUtil.class);

	@Autowired
	private UserManagerService userManagerService;

	@Autowired
	private RunAsMongoAdmin mongoAdminRunner;

	@Autowired
	private AppProp appProp;

	@Autowired
	private MongoRead read;

	private HashSet<String> testAccountNames = new HashSet<String>();

	@Autowired
	private MongoAppConfig mac;

	@Autowired
	private MongoTemplate ops;

	@Autowired
	private SubNodeUtil apiUtil;

	@Autowired
	private AclService aclService;

	@Autowired
	private MongoCreate create;

	@Autowired
	private MongoUpdate update;

	@Autowired
	private MongoAuth auth;

	private static SubNode systemRootNode;

	// todo-1: need to look into bulk-ops for doing this saveSession updating
	// tips:
	// https://stackoverflow.com/questions/26657055/spring-data-mongodb-and-bulk-update
	// BulkOperations ops = template.bulkOps(BulkMode.UNORDERED, Match.class);
	// for (User user : users) {
	// Update update = new Update();
	// ...
	// ops.updateOne(query(where("id").is(user.getId())), update);
	// }
	// ops.execute();
	//

	// private void initPageNodeFromClasspath(Session session, Node node, String
	// classpath) {
	// try {
	// Resource resource =
	// SpringContextUtil.getApplicationContext().getResource(classpath);
	// String content = XString.loadResourceIntoString(resource);
	// node.setProperty(JcrProp.CONTENT, content);
	// AccessControlUtil.makeNodePublic(session, node);
	// node.setProperty(JcrProp.DISABLE_INSERT, "y");
	// JcrUtil.save(session);
	// }
	// catch (Exception e) {
	// // IMPORTANT: don't rethrow from here, or this could blow up app
	// // initialization.
	// e.printStackTrace();
	// }
	// }

	/*
	 * We create these users just so there's an easy way to start doing multi-user
	 * testing (sharing nodes from user to user, etc) without first having to
	 * manually register users.
	 */
	public void createTestAccounts() {
		/*
		 * The testUserAccounts is a comma delimited list of user accounts where each
		 * user account is a colon-delimited list like username:password:email.
		 */
		final List<String> testUserAccountsList = XString.tokenize(appProp.getTestUserAccounts(), ",", true);
		if (testUserAccountsList == null) {
			return;
		}

		mongoAdminRunner.run((MongoSession session) -> {
			for (String accountInfo : testUserAccountsList) {
				log.debug("Verifying test Account: " + accountInfo);

				final List<String> accountInfoList = XString.tokenize(accountInfo, ":", true);
				if (accountInfoList == null || accountInfoList.size() != 3) {
					log.debug("Invalid User Info substring: " + accountInfo);
					continue;
				}

				String userName = accountInfoList.get(0);

				SubNode ownerNode = read.getUserNodeByUserName(session, userName);
				if (ownerNode == null) {
					log.debug("userName not found: " + userName + ". Account will be created.");
					SignupRequest signupReq = new SignupRequest();
					signupReq.setUserName(userName);
					signupReq.setPassword(accountInfoList.get(1));
					signupReq.setEmail(accountInfoList.get(2));

					userManagerService.signup(signupReq, true);
				} else {
					log.debug("account exists: " + userName);
				}

				/*
				 * keep track of these names, because some API methods need to know if a given
				 * account is a test account
				 */
				testAccountNames.add(userName);
			}
		});
	}

	public boolean isTestAccountName(String userName) {
		return testAccountNames.contains(userName);
	}

	/* Root path will start with '/' and then contain no other slashes */
	public boolean isRootPath(String path) {
		return path.startsWith("/") && path.substring(1).indexOf("/") == -1;
	}

	public Iterable<SubNode> findAllNodes(MongoSession session) {
		auth.requireAdmin(session);
		update.saveSession(session);
		return ops.findAll(SubNode.class);
	}

	public String getHashOfPassword(String password) {
		return Util.getHashOfString(password, 20);
	}

	public String getNodeReport() {
		int numDocs = 0;
		int totalJsonBytes = 0;
		MongoDatabase database = mac.mongoClient().getDatabase(MongoAppConfig.databaseName);
		MongoCollection<Document> col = database.getCollection("nodes");

		MongoCursor<Document> cur = col.find().iterator();
		try {
			while (cur.hasNext()) {
				Document doc = cur.next();
				totalJsonBytes += doc.toJson().length();
				numDocs++;
			}
		} catch (Exception ex) {
			throw ExUtil.wrapEx(ex);
		}

		/*
		 * todo-1: I have a 'formatMemory' written in javascript, and need to do same
		 * here or see if there's an apachie string function for it.
		 */
		float kb = totalJsonBytes / 1024f;
		return "Node Count: " + numDocs + "<br>Total JSON Size: " + kb + " KB";
	}

	/*
	 * Whenever we do something like reindex in a new way, we might need to
	 * reprocess every object, to generate any kind of auto-generated fields that
	 * need to be there before indexes build we call this.
	 * 
	 * For example when the path hash was introduced (i.e. SubNode.FIELD_PATH_HASH)
	 * we ran this to create all the path hashes so that a unique index could be
	 * built, because the uniqueness test would fail until we generated all the
	 * proper data, which required a modification on every node in the entire DB.
	 * 
	 * Note that MongoEventListener#onBeforeSave does execute even if all we are
	 * doing is reading nodes and then resaving them.
	 */
	// ********* DO NOT DELETE *********
	// (this is needed from time to time)
	public void reSaveAll(MongoSession session) {
		log.debug("Processing reSaveAll: Beginning Node Report: " + getNodeReport());

		// processAllNodes(session);
	}

	public void processAllNodes(MongoSession session) {
		// ValContainer<Long> nodesProcessed = new ValContainer<Long>(0L);

		// Query query = new Query();
		// Criteria criteria = Criteria.where(SubNode.FIELD_ACL).ne(null);
		// query.addCriteria(criteria);

		// saveSession(session);
		// Iterable<SubNode> iter = ops.find(query, SubNode.class);

		// iter.forEach((node) -> {
		// nodesProcessed.setVal(nodesProcessed.getVal() + 1);
		// if (nodesProcessed.getVal() % 1000 == 0) {
		// log.debug("reSave count: " + nodesProcessed.getVal());
		// }

		// // /*
		// // * NOTE: MongoEventListener#onBeforeSave runs in here, which is where some
		// of
		// // * the workload is done that pertains ot this reSave process
		// // */
		// save(session, node, true, false);
		// });
	}

	/* Returns true if there were actually some encryption keys removed */
	public boolean removeAllEncryptionKeys(SubNode node) {
		HashMap<String, AccessControl> aclMap = node.getAc();
		if (aclMap == null) {
			return false;
		}

		ValContainer<Boolean> keysRemoved = new ValContainer<Boolean>(false);
		aclMap.forEach((String key, AccessControl ac) -> {
			if (ac.getKey() != null) {
				ac.setKey(null);
				keysRemoved.setVal(true);
			}
		});

		return keysRemoved.getVal();
	}

	public boolean isImageAttached(SubNode node) {
		String mime = node.getStringProp(NodeProp.BIN_MIME.s());
		return ImageUtil.isImageMime(mime);
	}

	public ImageSize getImageSize(SubNode node) {
		return Convert.getImageSize(node);
	}

	public int dump(String message, Iterable<SubNode> iter) {
		int count = 0;
		log.debug("    " + message);
		for (SubNode node : iter) {
			log.debug("    DUMP node: " + XString.prettyPrint(node));
			count++;
		}
		log.debug("DUMP count=" + count);
		return count;
	}

	public void rebuildIndexes(MongoSession session) {
		dropAllIndexes(session);
		createAllIndexes(session);
	}

	public void createAllIndexes(MongoSession session) {
		try {
			// dropIndex(session, SubNode.class, SubNode.FIELD_PATH + "_1");
			dropIndex(session, SubNode.class, SubNode.FIELD_NAME + "_1");
		} catch (Exception e) {
			log.debug("no field name index found. ok. this is fine.");
		}
		log.debug("creating all indexes.");

		createUniqueIndex(session, SubNode.class, SubNode.FIELD_PATH_HASH);

		/*
		 * NOTE: Every non-admin owned noded must have only names that are prefixed with
		 * "UserName--" of the user. That is, prefixed by their username followed by two
		 * dashes
		 */
		createIndex(session, SubNode.class, SubNode.FIELD_NAME);

		createIndex(session, SubNode.class, SubNode.FIELD_ORDINAL);
		createIndex(session, SubNode.class, SubNode.FIELD_MODIFY_TIME, Direction.DESC);
		createIndex(session, SubNode.class, SubNode.FIELD_CREATE_TIME, Direction.DESC);
		createTextIndexes(session, SubNode.class);

		logIndexes(session, SubNode.class);
	}

	public void dropAllIndexes(MongoSession session) {
		auth.requireAdmin(session);
		update.saveSession(session);
		ops.indexOps(SubNode.class).dropAllIndexes();
	}

	public void dropIndex(MongoSession session, Class<?> clazz, String indexName) {
		auth.requireAdmin(session);
		log.debug("Dropping index: " + indexName);
		update.saveSession(session);
		ops.indexOps(clazz).dropIndex(indexName);
	}

	public void logIndexes(MongoSession session, Class<?> clazz) {
		StringBuilder sb = new StringBuilder();
		update.saveSession(session);
		List<IndexInfo> indexes = ops.indexOps(clazz).getIndexInfo();
		for (IndexInfo idx : indexes) {
			List<IndexField> indexFields = idx.getIndexFields();
			sb.append("INDEX EXISTS: " + idx.getName() + "\n");
			for (IndexField idxField : indexFields) {
				sb.append("    " + idxField.toString() + "\n");
			}
		}
		log.debug(sb.toString());
	}

	public void createUniqueIndex(MongoSession session, Class<?> clazz, String property) {
		auth.requireAdmin(session);
		update.saveSession(session);
		ops.indexOps(clazz).ensureIndex(new Index().on(property, Direction.ASC).unique());
	}

	public void createIndex(MongoSession session, Class<?> clazz, String property) {
		auth.requireAdmin(session);
		update.saveSession(session);
		ops.indexOps(clazz).ensureIndex(new Index().on(property, Direction.ASC));
	}

	public void createIndex(MongoSession session, Class<?> clazz, String property, Direction dir) {
		auth.requireAdmin(session);
		update.saveSession(session);
		ops.indexOps(clazz).ensureIndex(new Index().on(property, dir));
	}

	/*
	 * DO NOT DELETE.
	 * 
	 * I tried to create just ONE full text index, and i get exceptions, and even if
	 * i try to build a text index on a specific property I also get exceptions, so
	 * currently i am having to resort to using only the createTextIndexes() below
	 * which does the 'onAllFields' option which DOES work for some readonly
	 */
	// public void createUniqueTextIndex(MongoSession session, Class<?> clazz,
	// String property) {
	// requireAdmin(session);
	//
	// TextIndexDefinition textIndex = new
	// TextIndexDefinitionBuilder().onField(property).build();
	//
	// /* If mongo will not allow dupliate checks of a text index, i can simply take
	// a HASH of the
	// content text, and enforce that's unique
	// * and while i'm at it secondarily use it as a corruption check.
	// */
	// /* todo-2: haven't yet run my test case that verifies duplicate tree paths
	// are indeed
	// rejected */
	// DBObject dbo = textIndex.getIndexOptions();
	// dbo.put("unique", true);
	// dbo.put("dropDups", true);
	//
	// ops.indexOps(clazz).ensureIndex(textIndex);
	// }

	public void createTextIndexes(MongoSession session, Class<?> clazz) {
		auth.requireAdmin(session);

		TextIndexDefinition textIndex = new TextIndexDefinitionBuilder().onAllFields()
				// .onField(SubNode.FIELD_PROPERTIES+"."+NodeProp.CONTENT)
				.build();

		update.saveSession(session);
		ops.indexOps(clazz).ensureIndex(textIndex);
	}

	public void dropCollection(MongoSession session, Class<?> clazz) {
		auth.requireAdmin(session);
		ops.dropCollection(clazz);
	}

	public String regexDirectChildrenOfPath(String path) {
		path = XString.stripIfEndsWith(path, "/");
		return "^" + Pattern.quote(path) + "\\/([^\\/])*$";
	}

	/*
	 * todo-2: I think now that I'm including the trailing slash after path in this
	 * regex that I can remove the (.+) piece? I think i need to write some test
	 * cases just to test my regex functions!
	 * 
	 * todo-1: Also what's the 'human readable' description of what's going on here?
	 * substring or prefix? For performance we DO want this to be finding all nodes
	 * that 'start with' the path as opposed to simply 'contain' the path right? To
	 * make best use of indexes etc?
	 */
	public String regexRecursiveChildrenOfPath(String path) {
		path = XString.stripIfEndsWith(path, "/");
		return "^" + Pattern.quote(path) + "\\/(.+)$";
	}

	public SubNode createUser(MongoSession session, String user, String email, String password, boolean automated) {
		// if (PrincipalName.ADMIN.s().equals(user)) {
		// throw new RuntimeEx("createUser should not be called fror admin
		// user.");
		// }

		auth.requireAdmin(session);
		String newUserNodePath = NodeName.ROOT_OF_ALL_USERS + "/?";
		// todo-1: is user validated here (no invalid characters, etc. and invalid
		// flowpaths tested?)

		SubNode userNode = create.createNode(session, newUserNodePath, NodeType.ACCOUNT.s());
		ObjectId id = new ObjectId();
		userNode.setId(id);
		userNode.setOwner(id);
		userNode.setProp(NodeProp.USER.s(), user);
		userNode.setProp(NodeProp.EMAIL.s(), email);
		userNode.setProp(NodeProp.PWD_HASH.s(), getHashOfPassword(password));
		userNode.setProp(NodeProp.USER_PREF_EDIT_MODE.s(), false);
		userNode.setProp(NodeProp.USER_PREF_EDIT_MODE.s(), false);
		userNode.setProp(NodeProp.BIN_TOTAL.s(), 0);
		userNode.setProp(NodeProp.LAST_LOGIN_TIME.s(), 0);
		userNode.setProp(NodeProp.BIN_QUOTA.s(), Const.DEFAULT_USER_QUOTA);

		userNode.setContent("### Account: " + user);

		if (!automated) {
			userNode.setProp(NodeProp.SIGNUP_PENDING.s(), true);
		}

		update.save(session, userNode);
		return userNode;
	}

	public SubNode getSystemRootNode() {
		return systemRootNode;
	}

	public void initSystemRootNode() {
		systemRootNode = read.getNode(auth.getAdminSession(), "/r");
	}

	/*
	 * Initialize admin user account credentials into repository if not yet done.
	 * This should only get triggered the first time the repository is created, the
	 * first time the app is started.
	 * 
	 * The admin node is also the repository root node, so it owns all other nodes,
	 * by the definition of they way security is inheritive.
	 */
	public void createAdminUser(MongoSession session) {
		String adminUser = appProp.getMongoAdminUserName();

		SubNode adminNode = read.getUserNodeByUserName(auth.getAdminSession(), adminUser);
		if (adminNode == null) {
			adminNode = apiUtil.ensureNodeExists(session, "/", NodeName.ROOT, "Repository Root", NodeType.REPO_ROOT.s(),
					true, null, null);

			adminNode.setProp(NodeProp.USER.s(), PrincipalName.ADMIN.s());

			// todo-1: need to store ONLY hash of the password
			adminNode.setProp(NodeProp.USER_PREF_EDIT_MODE.s(), false);
			update.save(session, adminNode);

			apiUtil.ensureNodeExists(session, "/" + NodeName.ROOT, NodeName.USER, "Root of All Users", null, true, null,
					null);
			apiUtil.ensureNodeExists(session, "/" + NodeName.ROOT, NodeName.OUTBOX, "System Email Outbox", null, true,
					null, null);
		}

		createPublicNodes(session);
	}

	public void createPublicNodes(MongoSession session) {
		log.debug("creating PublicNodes");
		ValContainer<Boolean> created = new ValContainer<>();
		SubNode publicNode = apiUtil.ensureNodeExists(session, "/" + NodeName.ROOT, NodeName.PUBLIC, "Public", null,
				true, null, created);

		if (created.getVal()) {
			aclService.addPrivilege(session, publicNode, PrincipalName.PUBLIC.s(),
					Arrays.asList(PrivilegeType.READ.s()), null);
		}

		/*
		 * todo-1: update docs to say that admin is supposed to do something to
		 * initialize these two nodes (landing page, and userguide)
		 */
		created = new ValContainer<>();
		SubNode publicHome = apiUtil.ensureNodeExists(session, "/" + NodeName.ROOT + "/" + NodeName.PUBLIC, "home",
				"Public Home", null, true, null, created);
		log.debug("Public Home Node exists at id: " + publicHome.getId() + " path=" + publicHome.getPath());

		created = new ValContainer<>();
		SubNode userGuide = apiUtil.ensureNodeExists(session, "/" + NodeName.ROOT + "/" + NodeName.PUBLIC, "userguide",
				"User Guide", null, true, null, created);
		log.debug("UserGuide Node exists at id: " + userGuide.getId());

		/* Ensure Content folder is created and synced to file system */
		// SubNodePropertyMap props = new SubNodePropertyMap();
		// props.put(TYPES.FILE_SYNC_LINK.getName(), new
		// SubNodePropVal(appProp.publicContentFolder()));
		// SubNode contentNode = apiUtil.ensureNodeExists(session, "/" + NodeName.ROOT +
		// "/" + NodeName.PUBLIC, "content",
		// null, TYPES.FILE_SYNC_ROOT.getName(), true, props, created);

		// // ---------------------------------------------------------
		// // NOTE: Do not delete this. May need this example in the future. This is
		// // formerly the way we loaded the static
		// // site content (landing page, etc) for the app, before using the Folder
		// Synced
		// // "content" folder which we currently use.
		// // ImportZipService importZipService = (ImportZipService)
		// // SpringContextUtil.getBean(ImportZipService.class);
		// // importZipService.inputZipFileFromResource(session, "classpath:home.zip",
		// // publicNode, NodeName.HOME);
		// // ---------------------------------------------------------
		// SubNode node = getNode(session, "/" + NodeName.ROOT + "/" + NodeName.PUBLIC +
		// "/" + NodeName.HOME);
		// if (node == null) {
		// log.debug("Public node didn't exist. Creating.");
		// node = getNode(session, "/" + NodeName.ROOT + "/" + NodeName.PUBLIC + "/" +
		// NodeName.HOME);
		// if (node == null) {
		// log.debug("Error reading node that was just imported.");
		// } else {
		// long childCount = getChildCount(node);
		// log.debug("Verified Home Node has " + childCount + " children.");
		// }
		// } else {
		// long childCount = getChildCount(node);
		// log.debug("Home node already existed with " + childCount + " children");
		// }
	}
}
