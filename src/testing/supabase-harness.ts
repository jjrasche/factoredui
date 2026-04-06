import { createClient } from "@supabase/supabase-js";

const LOCAL_SUPABASE_URL = "http://127.0.0.1:54321";
const LOCAL_ANON_KEY =
  "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZS1kZW1vIiwicm9sZSI6ImFub24iLCJleHAiOjE5ODM4MTI5OTZ9.CRXP1A7WOeoJeXxjNni43kdQwgnWNReilDMblYTn_I0";
const LOCAL_SERVICE_ROLE_KEY =
  "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZS1kZW1vIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImV4cCI6MTk4MzgxMjk5Nn0.EGIM96RAZx35lJzdJsyH-qQwv8Hdp7fsn3W0YpN81IU";

type ObserveClient = ReturnType<typeof createServiceClient>;

export function createServiceClient() {
  return createClient(LOCAL_SUPABASE_URL, LOCAL_SERVICE_ROLE_KEY, {
    db: { schema: "observe" },
    auth: { persistSession: false },
  });
}

export function createAnonClient() {
  return createClient(LOCAL_SUPABASE_URL, LOCAL_ANON_KEY, {
    db: { schema: "observe" },
    auth: { persistSession: false },
  });
}

interface TestUser {
  id: string;
  email: string;
  password: string;
}

export async function createTestUser(
  serviceClient: ObserveClient,
): Promise<TestUser> {
  const email = `test-${crypto.randomUUID().slice(0, 8)}@observe.test`;
  const password = "test-password-123!";

  const { data, error } = await serviceClient.auth.admin.createUser({
    email,
    password,
    email_confirm: true,
  });

  if (error) throw new Error(`Failed to create test user: ${error.message}`);
  return { id: data.user.id, email, password };
}

export async function deleteTestUser(
  serviceClient: ObserveClient,
  userId: string,
): Promise<void> {
  await serviceClient.auth.admin.deleteUser(userId);
}

export async function signInTestUser(
  anonClient: ObserveClient,
  user: TestUser,
): Promise<ObserveClient> {
  const { error } = await anonClient.auth.signInWithPassword({
    email: user.email,
    password: user.password,
  });
  if (error) throw new Error(`Failed to sign in test user: ${error.message}`);
  return anonClient;
}
