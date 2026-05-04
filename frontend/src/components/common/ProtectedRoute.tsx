import { Navigate, useLocation } from 'react-router';
import { useAuthStore } from '../../stores/authStore';
import type { Role } from '../../types';

interface ProtectedRouteProps {
  children: React.ReactNode;
  allowedRoles?: Role[];
}

export default function ProtectedRoute({ children, allowedRoles }: ProtectedRouteProps) {
  const { isAuthenticated, user } = useAuthStore();
  const location = useLocation();

  if (!isAuthenticated) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }

  // Force first-login password rotation: until cleared, the user can only access
  // /change-password (and trying to load any other protected route bounces back).
  if (user?.mustChangePassword && location.pathname !== '/change-password') {
    return <Navigate to="/change-password" replace />;
  }

  // Force CUSTOMERs to set a transaction PIN before doing anything else.
  if (
    user?.role === 'CUSTOMER' &&
    user?.pinSet === false &&
    location.pathname !== '/setup-pin'
  ) {
    return <Navigate to="/setup-pin" replace />;
  }

  if (allowedRoles && user && !allowedRoles.includes(user.role)) {
    return <Navigate to="/dashboard" replace />;
  }

  return <>{children}</>;
}
