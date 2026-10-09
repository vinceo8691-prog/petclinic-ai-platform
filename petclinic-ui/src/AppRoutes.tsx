import { Navigate, Route, Routes } from 'react-router-dom'
import { AppShell } from './components/AppShell'
import { AddOwnerPage } from './features/owners/AddOwnerPage'
import { OwnerDetailsPage } from './features/owners/OwnerDetailsPage'
import { OwnersPage } from './features/owners/OwnersPage'

export function AppRoutes() {
  return (
    <Routes>
      <Route element={<AppShell />}>
        <Route path="/owners" element={<OwnersPage />} />
        <Route path="/owners/new" element={<AddOwnerPage />} />
        <Route path="/owners/:ownerId" element={<OwnerDetailsPage />} />
        <Route path="*" element={<Navigate to="/owners" replace />} />
      </Route>
    </Routes>
  )
}
