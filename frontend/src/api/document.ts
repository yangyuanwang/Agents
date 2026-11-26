import { request } from './http'

export type DocumentStatus = 'ACTIVE' | 'UPLOADED' | 'PROCESSING' | 'COMPLETED' | 'FAILED'

export interface DocumentVersion {
  id: string
  documentId: string
  versionNo: number
  fileObjectId: string
  sha256: string
  mediaType: string
  byteSize: number
  originalFilename: string | null
  status: DocumentStatus
  createdAt: string
  activatedAt?: string | null
}

export interface DocumentRecord {
  id: string
  tenantId: string
  knowledgeBaseId: string
  displayName: string
  status: DocumentStatus
  activeVersionId?: string | null
  versionCount: number
  activeVersion?: DocumentVersion | null
  createdAt: string
  updatedAt: string
}

export interface DocumentPage {
  items: DocumentRecord[]
  total: number
  page: number
  size: number
}

export interface FileObjectUploadResult {
  id: string
  tenantId: string
  sha256: string
  byteSize: number
  mediaType: string
  storageStatus: string
  createdAt: string
}

export function listDocuments(params: {
  knowledgeBaseId: string
  page: number
  size: number
  keyword?: string
}): Promise<DocumentPage> {
  return request<DocumentPage>({ method: 'get', url: '/documents', params, silent: true })
}

export function getDocument(id: string, knowledgeBaseId: string): Promise<DocumentRecord> {
  return request<DocumentRecord>({ method: 'get', url: `/documents/${id}`, params: { knowledgeBaseId }, silent: true })
}

export function createDocument(payload: { knowledgeBaseId: string; displayName: string }): Promise<DocumentRecord> {
  return request<DocumentRecord>({ method: 'post', url: '/documents', data: payload, silent: true })
}

export function updateDocument(
  id: string,
  knowledgeBaseId: string,
  payload: { displayName: string }
): Promise<DocumentRecord> {
  return request<DocumentRecord>({ method: 'put', url: `/documents/${id}`, params: { knowledgeBaseId }, data: payload, silent: true })
}

export function deleteDocument(id: string, knowledgeBaseId: string): Promise<void> {
  return request<void>({ method: 'delete', url: `/documents/${id}`, params: { knowledgeBaseId }, silent: true })
}

export function uploadFile(
  knowledgeBaseId: string,
  file: File,
  onProgress?: (percent: number) => void
): Promise<FileObjectUploadResult> {
  const form = new FormData()
  form.append('knowledgeBaseId', knowledgeBaseId)
  form.append('file', file)
  return request<FileObjectUploadResult>({
    method: 'post',
    url: '/files/upload',
    data: form,
    timeout: 120000,
    onUploadProgress: (event) => {
      if (event.total) onProgress?.(Math.round((event.loaded * 100) / event.total))
    },
    silent: true
  })
}

export function listDocumentVersions(documentId: string): Promise<DocumentVersion[]> {
  return request<DocumentVersion[]>({ method: 'get', url: `/documents/${documentId}/versions`, silent: true })
}

export function getDocumentVersion(documentId: string, versionId: string): Promise<DocumentVersion> {
  return request<DocumentVersion>({ method: 'get', url: `/documents/${documentId}/versions/${versionId}`, silent: true })
}

export function createDocumentVersion(
  documentId: string,
  payload: { fileObjectId: string; originalFilename: string }
): Promise<DocumentVersion> {
  return request<DocumentVersion>({ method: 'post', url: `/documents/${documentId}/versions`, data: payload, silent: true })
}

export function setActiveDocumentVersion(documentId: string, versionId: string): Promise<DocumentRecord> {
  return request<DocumentRecord>({ method: 'put', url: `/documents/${documentId}/active-version`, data: { versionId }, silent: true })
}
