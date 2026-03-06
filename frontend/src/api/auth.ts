import request from './axios'

export const authApi = {
  login: (data: { username: string; password: string }) =>
    request.post('/auth/login', data),

  register: (data: { username: string; password: string; displayName?: string }) =>
    request.post('/auth/register', data),

  me: () => request.get('/auth/me'),

  changePassword: (data: { oldPassword: string; newPassword: string }) =>
    request.put('/auth/me/password', data),

  listApiKeys: () => request.get('/auth/api-keys'),

  createApiKey: (data: { keyName: string; expiresAt?: string }) =>
    request.post('/auth/api-keys', data),

  revokeApiKey: (id: number) => request.delete(`/auth/api-keys/${id}`)
}
