const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ADMIN_UID = "admin_rahifa_01";
const ADMIN_EMAIL = "info.nkbtc@gmail.com";
const USER_UID = "reader_user_02";
const USER_EMAIL = "reader@example.com";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function adminContext() {
  return testEnv.authenticatedContext(ADMIN_UID, {
    email: ADMIN_EMAIL,
    email_verified: true,
  });
}

function spoofedUnverifiedAdminContext() {
  return testEnv.authenticatedContext("spoof_uid", {
    email: ADMIN_EMAIL,
    email_verified: false,
  });
}

function normalSignedInContext() {
  return testEnv.authenticatedContext(USER_UID, {
    email: USER_EMAIL,
    email_verified: true,
  });
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Public readers: can list/get published books, categories, active announcements without sign-in", async () => {
  const now = new Date(Date.now() - 5000);
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await db.collection("categories").doc("cat_fiction").set({
      name: "Fiction",
      description: "Literary fiction and classics",
      bookCount: 1,
      createdAt: now,
      updatedAt: now,
    });
    await db.collection("books").doc("book_pub").set({
      title: "The Lantern Courtyard",
      author: "A. Rahman",
      categoryId: "cat_fiction",
      categoryName: "Fiction",
      description: "An evening tale.",
      coverUrl: "https://example.com/cover.jpg",
      pdfUrl: "https://example.com/book.pdf",
      published: true,
      featured: true,
      pageCount: 42,
      createdBy: ADMIN_UID,
      createdAt: now,
      updatedAt: now,
    });
    await db.collection("books").doc("book_draft").set({
      title: "Unpublished Draft",
      author: "A. Rahman",
      categoryId: "cat_fiction",
      categoryName: "Fiction",
      description: "Hidden draft.",
      coverUrl: "",
      pdfUrl: "https://example.com/draft.pdf",
      published: false,
      featured: false,
      pageCount: 10,
      createdBy: ADMIN_UID,
      createdAt: now,
      updatedAt: now,
    });
    await db.collection("announcements").doc("ann_1").set({
      title: "Welcome to Rahifa Books",
      message: "New titles added weekly.",
      active: true,
      createdAt: now,
      updatedAt: now,
    });
    await db.collection("announcements").doc("ann_hidden").set({
      title: "Internal Draft",
      message: "Not active",
      active: false,
      createdAt: now,
      updatedAt: now,
    });
  });

  const unauthDb = testEnv.unauthenticatedContext().firestore();

  // Client-aligned queries succeed
  await assertSucceeds(unauthDb.collection("categories").get());
  await assertSucceeds(
    unauthDb.collection("books").where("published", "==", true).get()
  );
  await assertSucceeds(unauthDb.collection("books").doc("book_pub").get());
  await assertSucceeds(
    unauthDb.collection("announcements").where("active", "==", true).get()
  );

  // Unfiltered queries or reading unpublished/inactive documents fail
  await assertFails(unauthDb.collection("books").get());
  await assertFails(unauthDb.collection("books").doc("book_draft").get());
  await assertFails(unauthDb.collection("announcements").get());
  await assertFails(unauthDb.collection("announcements").doc("ann_hidden").get());
});

test("Normal users and spoofed unverified admins: cannot write books, categories, announcements, or admins", async () => {
  const now = new Date(Date.now() - 5000);
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const userDb = normalSignedInContext().firestore();
  const spoofDb = spoofedUnverifiedAdminContext().firestore();

  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("categories").doc("cat_1").set({
      name: "History",
      description: "Historical works",
      bookCount: 0,
      createdAt: now,
      updatedAt: now,
    });
  });

  const categoryPayload = {
    name: "Hacked",
    description: "Nope",
    bookCount: 0,
    createdAt: now,
    updatedAt: now,
  };
  const bookPayload = {
    title: "Unauthorized Book",
    author: "Hacker",
    categoryId: "cat_1",
    categoryName: "History",
    description: "Desc",
    coverUrl: "",
    pdfUrl: "https://example.com/a.pdf",
    published: true,
    featured: false,
    pageCount: 5,
    createdBy: USER_UID,
    createdAt: now,
    updatedAt: now,
  };

  await assertFails(unauthDb.collection("categories").doc("cat_2").set(categoryPayload));
  await assertFails(userDb.collection("categories").doc("cat_2").set(categoryPayload));
  await assertFails(spoofDb.collection("categories").doc("cat_2").set(categoryPayload));

  await assertFails(unauthDb.collection("books").doc("book_2").set(bookPayload));
  await assertFails(userDb.collection("books").doc("book_2").set(bookPayload));
  await assertFails(
    spoofDb.collection("books").doc("book_2").set({ ...bookPayload, createdBy: "spoof_uid" })
  );

  await assertFails(
    userDb.collection("admins").doc(USER_UID).set({ email: USER_EMAIL })
  );
  await assertFails(
    userDb.collection("announcements").doc("ann_2").set({
      title: "Fake",
      message: "Fake",
      active: true,
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("Verified Admin: full CRUD on categories, books, and announcements with strict validation", async () => {
  const adminDb = adminContext().firestore();
  const created = new Date(Date.now() - 10000);

  // Create category
  await assertSucceeds(
    adminDb.collection("categories").doc("cat_poetry").set({
      name: "Poetry",
      description: "Verse and classic poems",
      bookCount: 0,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Rename category
  await assertSucceeds(
    adminDb.collection("categories").doc("cat_poetry").update({
      name: "Classic Poetry",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Create book (must reference existing category, no @DocumentId 'id' field)
  await assertSucceeds(
    adminDb.collection("books").doc("book_1").set({
      title: "Diwan of Stars",
      author: "Rahifa Press",
      categoryId: "cat_poetry",
      categoryName: "Classic Poetry",
      description: "Collected verses.",
      coverUrl: "https://example.com/diwan.jpg",
      pdfUrl: "https://example.com/diwan.pdf",
      published: false,
      featured: false,
      pageCount: 120,
      createdBy: ADMIN_UID,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Reject creating book with missing category or extra ghost field or @DocumentId 'id'
  await assertFails(
    adminDb.collection("books").doc("book_bad_cat").set({
      title: "Bad Cat",
      author: "Author",
      categoryId: "cat_nonexistent",
      categoryName: "None",
      description: "",
      coverUrl: "",
      pdfUrl: "https://example.com/x.pdf",
      published: true,
      featured: false,
      pageCount: 10,
      createdBy: ADMIN_UID,
      createdAt: created,
      updatedAt: created,
    })
  );
  await assertFails(
    adminDb.collection("books").doc("book_with_id").set({
      id: "book_with_id",
      title: "Extra ID",
      author: "Author",
      categoryId: "cat_poetry",
      categoryName: "Classic Poetry",
      description: "",
      coverUrl: "",
      pdfUrl: "https://example.com/x.pdf",
      published: true,
      featured: false,
      pageCount: 10,
      createdBy: ADMIN_UID,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Partial update: publish & feature book
  await assertSucceeds(
    adminDb.collection("books").doc("book_1").update({
      published: true,
      featured: true,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Immutable createdAt/createdBy tampering rejected
  await assertFails(
    adminDb.collection("books").doc("book_1").update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Admin can list all books (both published & drafts)
  await assertSucceeds(adminDb.collection("books").get());

  // Announcement CRUD
  await assertSucceeds(
    adminDb.collection("announcements").doc("ann_main").set({
      title: "Eid Reading Collection",
      message: "Explore newly published titles.",
      active: true,
      createdAt: created,
      updatedAt: created,
    })
  );
  await assertSucceeds(
    adminDb.collection("announcements").doc("ann_main").update({
      active: false,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Delete book, category, announcement
  await assertSucceeds(adminDb.collection("books").doc("book_1").delete());
  await assertSucceeds(adminDb.collection("categories").doc("cat_poetry").delete());
  await assertSucceeds(adminDb.collection("announcements").doc("ann_main").delete());
});
